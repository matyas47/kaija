// KaijaController.sc
// Controller layer for Kaija.
// Owns the spectral model, voice pool, scale, MIDI responders,
// and listener/callback system.
//
// Parallel structure to KassiaController but centred on note events
// rather than continuous morphing. The view calls controller methods;
// the controller notifies the view of state changes.
//
// Requires: KaijaSpectralModel.sc, KaijaSynth.sc, KaijaScale.sc

KaijaController {

	var <model;          // KaijaSpectralModel
	var <voices;         // Array of KaijaSynth (voice pool)
	var <scale;          // KaijaScale
	var <presetBank;     // PresetBank
	var <bankPath;       // current bank file path for auto-save
	var listeners;       // Dictionary of Arrays of callbacks
	var server;          // SC server

	// MIDI state
	var midiChannel;     // nil = omni, 0–15 = specific channel
	var <timbreCCNum;    // CC number for index modulation
	var <timbreRange;    // +/- index range for CC modulation
	var <pitchBendRange; // pitch bend range in semitones
	var sustainPedal;    // Boolean — CC64 sustain state
	var heldNotes;       // Dictionary: midiNote -> voiceIndex
	var sustainedNotes;  // Set of notes held by sustain pedal after note-off

	// Voice allocation
	var voiceIndex;

	// MIDI responders
	var noteOnResponder, noteOffResponder, ccResponder, bendResponder;

	// Synth params (shared across all voices)
	var synthParams;
	var synthParamsArgs;   // cached flat args array, rebuilt when synthParams changes

	*new { |model, voices, scale, server|
		^super.new.init(model, voices, scale, server)
	}

	init { |m, v, sc, srv|
		model          = m;
		voices         = v;
		scale          = sc ?? { KaijaScale.new };
		server         = srv ?? { Server.default };
		listeners      = Dictionary.new;
		presetBank     = PresetBank.new;
		bankPath       = nil;
		presetBank.onChanged({ |names| this.notify(\presets, names) });
		midiChannel    = nil;
		timbreCCNum    = 74;
		timbreRange    = 3.0;
		pitchBendRange = 2.0;  // semitones
		sustainPedal   = false;
		heldNotes      = Dictionary.new;
		sustainedNotes = Set.new;
		voiceIndex     = 0;
		synthParams    = (
			master:       0.5,
			vcfFreq:      2000,
			vcfRQ:        0.35,
			vcfEnvAmt:    0.5,
			vcfLFORate:   0.5,
			vcfLFODepth:  0.0,
			noiseAmp:     0.0,
			attack:       0.01,
			decay:        0.3,
			sustain:      0.7,
			release:      0.5,
			drive:        1.0,
			vowelPos:     1.5
		);
		this.prRebuildSynthParamsArgs;
		^this
	}

	// Rebuild the cached args representation of synthParams.
	// Called when any synthParam changes — see set/loadPreset.
	prRebuildSynthParamsArgs {
		var arr = Array.new(synthParams.size * 2);
		synthParams.keysValuesDo({ |k, v| arr.add(k); arr.add(v) });
		synthParamsArgs = arr;
	}

	// ------------------------------------------------------------------
	// Listener system (identical pattern to KassiaController)
	// ------------------------------------------------------------------

	addListener { |key, func|
		listeners[key] = listeners[key].add(func);
	}

	removeListener { |key, func|
		if(func.isNil) {
			listeners.removeAt(key);
		} {
			listeners[key] = listeners[key].select({ |f| f != func });
		};
	}

	notify { |key ...args|
		var fns = listeners[key];
		if(fns.notNil) { fns.do({ |f| f.valueArray(args) }) };
	}

	// ------------------------------------------------------------------
	// Spectral model parameters
	// ------------------------------------------------------------------

	setRatio { |r|
		model.setRatio(r);
		this.prPushPartialsToAll;
		this.notify(\ratio,    model.ratio);
		this.notify(\modHz,    model.modHz);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	setIndex { |i|
		model.setIndex(i);
		this.prPushPartialsToAll;
		this.notify(\index,    model.index);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	setTilt { |t|
		model.setTilt(t);
		this.prPushPartialsToAll;
		this.notify(\tilt,     model.tilt);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	refreshPartials {
		this.prPushPartialsToAll;
		// Single batched notification — view re-reads everything in one defer
		this.notify(\refresh,
			model.carrier, model.ratio, model.index, model.tilt,
			model.modHz, model.absFreqs, model.amps);
	}

	// ------------------------------------------------------------------
	// Synth parameters (shared across all voices)
	// ------------------------------------------------------------------

	set { |key, val|
		synthParams[key] = val;
		this.prRebuildSynthParamsArgs;
		voices.do({ |v| v.set(key, val) });
		this.notify(key, val);
	}

	setPartialParam { |key, values|
		voices.do({ |v| v.setPartialParam(key, values) });
		this.notify(key, values);
	}

	setPartialParamAt { |key, index, val|
		voices.do({ |v| v.setPartialParamAt(key, index, val) });
		this.notify(key, voices[0].getState(key));
	}

	randomisePhases {
		var phases;
		phases = Array.fill(voices[0].num, { 1.0.rand });
		voices.do({ |v| v.setPartialParam(\phase, phases) });
		this.notify(\phase, phases);
	}

	// ------------------------------------------------------------------
	// MIDI configuration
	// ------------------------------------------------------------------

	setMidiChannel { |chan|
		midiChannel = chan;
		this.notify(\midiChannel, midiChannel);
	}

	setTimbreCCNum { |num|
		timbreCCNum = num.asInteger;
		this.notify(\timbreCCNum, timbreCCNum);
	}

	setTimbreRange { |range|
		timbreRange = range.asFloat.max(0.1);
		this.notify(\timbreRange, timbreRange);
	}

	setPitchBendRange { |semitones|
		pitchBendRange = semitones.asFloat.max(0.5);
		this.notify(\pitchBendRange, pitchBendRange);
	}

	// Release all voices immediately and clear note tracking — use when notes get stuck
	flushMIDI {
		voices.do({ |v| v.freeSynth });
		heldNotes.clear;
		sustainedNotes.clear;
		sustainPedal   = false;
		this.notify(\voiceActivity, Array.fill(voices.size, { false }));
	}

	// ------------------------------------------------------------------
	// Presets
	// ------------------------------------------------------------------

	savePreset { |name|
		var snap, v0;
		v0   = voices[0];
		snap = (
			ratio:       model.ratio,
			index:       model.index,
			tilt:        model.tilt,
			levels:      v0.getState(\levels).copy,
			phase:       v0.getState(\phase).copy,
			partAtk:     v0.getState(\partAtk).copy,
			partRel:     v0.getState(\partRel).copy
		);
		synthParams.keysValuesDo({ |k, v| snap[k] = v });
		presetBank.save(name, snap);
		if(bankPath.notNil) { presetBank.writeToFile(bankPath) };
		this.notify(\presets, presetBank.names);
	}

	loadPreset { |name|
		var snap;
		snap = presetBank.load(name);
		if(snap.isNil) { ("KaijaController: preset not found: " ++ name).warn; ^this };

		// Set model parameters directly (no per-setter recompute/push)
		if(snap[\ratio].notNil) { model.ratio = snap[\ratio].asFloat.clip(0.125, 8.0) };
		if(snap[\index].notNil) { model.index = snap[\index].asFloat.clip(0.0, 10.0) };
		if(snap[\tilt].notNil)  { model.tilt  = snap[\tilt].asFloat.clip(-1.0, 1.0) };
		model.compute;

		// Apply per-partial state
		if(snap[\levels].notNil)  { this.setPartialParam(\levels,  snap[\levels]) };
		if(snap[\phase].notNil)   { this.setPartialParam(\phase,   snap[\phase]) };
		if(snap[\partAtk].notNil) { this.setPartialParam(\partAtk, snap[\partAtk]) };
		if(snap[\partRel].notNil) { this.setPartialParam(\partRel, snap[\partRel]) };

		// Apply synth params — update synthParams dict and push to voices,
		// but defer the cache rebuild to the end.
		synthParams.keys.do({ |k|
			if(snap[k].notNil) {
				synthParams[k] = snap[k];
				voices.do({ |v| v.set(k, snap[k]) });
				this.notify(k, snap[k]);
			};
		});
		this.prRebuildSynthParamsArgs;

		// Single refresh push — recomputes model, pushes partials, batches UI notify
		this.refreshPartials;
		this.notify(\presetLoaded, name);
	}

	deletePreset { |name|
		presetBank.delete(name);
		if(bankPath.notNil and: { presetBank.size > 0 }) {
			presetBank.writeToFile(bankPath);
		} {
			// Bank emptied — clear path so next save starts fresh
			if(presetBank.size == 0) { bankPath = nil };
		};
		this.notify(\presets, presetBank.names);
	}

	writePresets { |path|
		bankPath = path;
		^presetBank.writeToFile(path)
	}

	readPresets { |path|
		var result;
		result = presetBank.readFromFile(path);
		if(result) {
			bankPath = path;
			this.notify(\presets, presetBank.names);
			if(presetBank.size > 0) {
				this.loadPreset(presetBank.names[0]);
			};
		};
		^result
	}

	// ------------------------------------------------------------------
	// MIDI setup / teardown
	// ------------------------------------------------------------------

	startMIDI {
		MIDIClient.init;
		MIDIIn.connectAll;

		noteOnResponder = MIDIFunc.noteOn({ |vel, note, chan|
			if(midiChannel.isNil or: { chan == midiChannel }) {
				this.prNoteOn(note, vel / 127.0);
			};
		});

		noteOffResponder = MIDIFunc.noteOff({ |vel, note, chan|
			if(midiChannel.isNil or: { chan == midiChannel }) {
				this.prNoteOff(note);
			};
		});

		// Pitch bend — maps ±8192 to ±pitchBendRange semitones
		bendResponder = MIDIFunc.bend({ |val, chan|
			if(midiChannel.isNil or: { chan == midiChannel }) {
				var semitones;
				semitones = (val - 8192) / 8192.0 * pitchBendRange;
				voices.do({ |v|
					if(v.node.notNil) { v.node.set(\pitchBend, semitones) };
				});
			};
		});

		ccResponder = MIDIFunc.cc({ |val, num, chan|
			if(midiChannel.isNil or: { chan == midiChannel }) {
				// Mod wheel (CC1) → vowelPos
				if(num == 1) {
					var pos = val / 127.0 * 4.0;
					voices.do({ |v|
						if(v.node.notNil) { v.node.set(\vowelPos, pos) };
					});
					this.notify(\modWheel, pos);
				};
				// Sustain pedal (CC64)
				if(num == 64) { this.prSustain(val >= 64) };
				// Timbre CC → additive index modulation
				if(num == timbreCCNum) {
					var delta, newIndex;
					delta    = (val - 64) / 64.0 * timbreRange;
					newIndex = (model.index + delta).clip(0.0, 10.0);
					model.setIndex(newIndex);
					this.prPushPartialsToAll;
					this.notify(\indexCC, newIndex);
				};
			};
		});
	}

	stopMIDI {
		if(noteOnResponder.notNil)  { noteOnResponder.free;  noteOnResponder = nil };
		if(noteOffResponder.notNil) { noteOffResponder.free; noteOffResponder = nil };
		if(ccResponder.notNil)      { ccResponder.free;      ccResponder = nil };
		if(bendResponder.notNil)    { bendResponder.free;    bendResponder = nil };
	}

	// ------------------------------------------------------------------
	// Scale
	// ------------------------------------------------------------------

	loadScale { |path|
		var result;
		result = scale.loadFile(path);
		if(result) { this.notify(\scale, scale.name) };
		^result
	}

	clearScale {
		scale.clear;
		this.notify(\scale, scale.name);
	}

	// ------------------------------------------------------------------
	// Startup / shutdown
	// ------------------------------------------------------------------

	play { |srv|
		server = srv ?? { Server.default };
		// Initialise voice level state from the model so the UI shows
		// FM-derived amplitudes before the user touches anything.
		// Real audio output uses these levels too once notes start.
		voices.do({ |v| v.setPartialParam(\levels, model.amps) });
		this.refreshPartials;
	}

	free {
		this.stopMIDI;
		voices.do({ |v| v.freeSynth });
		this.notify(\voiceActivity, Array.fill(voices.size, { false }));
	}

	// ------------------------------------------------------------------
	// Private — note event handling
	// ------------------------------------------------------------------

	prNoteOn { |note, velocity|
		var freq, vIdx, voice, args;

		freq  = scale.freqAt(note);
		vIdx  = this.prAllocVoice;
		voice = voices[vIdx];

		// If this note is already playing, release its existing voice first
		if(heldNotes[note].notNil) {
			voices[heldNotes[note]].noteOff;
		};

		heldNotes[note] = vIdx;
		sustainedNotes.remove(note);

		// Build args array combining per-note values, partial state, and
		// cached synthParams. Bundled into one OSC message at Synth creation.
		args = [
			\root,      freq,
			\velocity,  velocity.sqrt,
			\gate,      1,
			\ratios,    model.freqs,
			\levels,    voice.getState(\levels),
			\phase,     voice.getState(\phase),
			\partAtk,   voice.getState(\partAtk),
			\partRel,   voice.getState(\partRel)
		] ++ synthParamsArgs;

		voice.noteOnWithArgs(args, server);

		this.notify(\voiceActivity, this.prVoiceActivity);
		this.notify(\noteOn, note, freq, velocity);
	}

	prNoteOff { |note|
		if(sustainPedal) {
			// Hold note until pedal is released
			sustainedNotes.add(note);
		} {
			this.prReleaseNote(note);
		};
	}

	prReleaseNote { |note|
		var vIdx;
		vIdx = heldNotes[note];
		if(vIdx.notNil) {
			voices[vIdx].noteOff;
			heldNotes.removeAt(note);
			this.notify(\voiceActivity, this.prVoiceActivity);
			this.notify(\noteOff, note);
		};
	}

	prSustain { |state|
		sustainPedal = state;
		if(state.not) {
			// Release all notes that had their note-off while pedal was held.
			// prReleaseNote only mutates heldNotes, not sustainedNotes,
			// so iterating directly is safe.
			sustainedNotes.do({ |note| this.prReleaseNote(note) });
			sustainedNotes.clear;
		};
		this.notify(\sustain, sustainPedal);
	}

	// Round-robin voice allocation
	prAllocVoice {
		var idx;
		idx        = voiceIndex;
		voiceIndex = (voiceIndex + 1) % voices.size;
		^idx
	}

	// Returns an Array of Booleans — true if voice has an active node
	prVoiceActivity {
		^voices.collect({ |v| v.node.notNil })
	}

	// Push current partials to all active voices.
	// Pushes BOTH ratios and levels — the model's amps are derived for the
	// current ratio/index/tilt, so changing those parameters means the
	// previous levels are no longer meaningful. This is standard FM synth
	// behavior: changing the spectrum resets levels to model output.
	prPushPartialsToAll {
		voices.do({ |v|
			if(v.node.notNil) {
				v.setPartialParam(\ratios, model.freqs);
				v.setPartialParam(\levels, model.amps);
			};
		});
	}

	// Push model amplitudes to all voice level state (active or not).
	// Used at startup so the UI reflects the model from the beginning.
	prPushModelLevelsToAll {
		voices.do({ |v| v.setPartialParam(\levels, model.amps) });
	}

}
