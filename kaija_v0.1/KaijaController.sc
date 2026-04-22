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

	*new { |model, voices, scale, server|
		^super.new.init(model, voices, scale, server)
	}

	init { |m, v, sc, srv|
		model          = m;
		voices         = v;
		scale          = sc ?? { KaijaScale.new };
		server         = srv ?? { Server.default };
		listeners      = Dictionary.new;
		midiChannel    = nil;
		timbreCCNum    = 74;
		timbreRange    = 3.0;
		pitchBendRange = 2.0;  // semitones
		sustainPedal   = false;
		heldNotes      = Dictionary.new;
		sustainedNotes = Set.new;
		voiceIndex     = 0;
		synthParams    = (
			master:       0.15,
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
			vowelMix:     0.0,
			vowelPos:     1.5,
			vowelRQ:      1.0
		);
		^this
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
		listeners[key].do({ |f| f.valueArray(args) });
	}

	// ------------------------------------------------------------------
	// Spectral model parameters
	// ------------------------------------------------------------------

	setCarrier { |hz|
		model.setCarrier(hz);
		this.prPushPartialsToAll;
		this.notify(\carrier,  model.carrier);
		this.notify(\modHz,    model.modHz);
		this.notify(\partials, model.absFreqs, model.amps);
	}

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
		this.notify(\carrier,  model.carrier);
		this.notify(\ratio,    model.ratio);
		this.notify(\index,    model.index);
		this.notify(\tilt,     model.tilt);
		this.notify(\modHz,    model.modHz);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	// ------------------------------------------------------------------
	// Synth parameters (shared across all voices)
	// ------------------------------------------------------------------

	set { |key, val|
		synthParams[key] = val;
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
		heldNotes      = Dictionary.new;
		sustainedNotes = Set.new;
		sustainPedal   = false;
		this.notify(\voiceActivity, Array.fill(voices.size, { false }));
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
		var freq, vIdx, voice;

		freq  = scale.freqAt(note);
		vIdx  = this.prAllocVoice;
		voice = voices[vIdx];

		// If this note is already playing, release it first
		if(heldNotes[note].notNil) {
			voices[heldNotes[note]].noteOff;
		};

		heldNotes[note] = vIdx;
		sustainedNotes.remove(note);

		voice.noteOn(freq, velocity, synthParams[\master], server);

		// Push ratios always; push levels only if voice is fresh (all zero)
		// so manual level adjustments survive note retriggers.
		if(voice.node.notNil) {
			voice.node.setn(\ratios, model.freqs);
			if(voice.getState(\levels).every({ |l| l == 0.0 })) {
				voice.setPartials(model.freqs, model.amps);
			};
		};
		synthParams.keysValuesDo({ |k, v| voice.set(k, v) });

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
			// Release all notes that had their note-off while pedal was held
			sustainedNotes.do({ |note| this.prReleaseNote(note) });
			sustainedNotes = Set.new;
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

	// Push current partials to all active voices
	prPushPartialsToAll {
		voices.do({ |v|
			if(v.node.notNil) {
				v.setPartials(model.freqs, model.amps);
			};
		});
	}

}
