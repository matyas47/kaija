// KassiaController.sc
// Controller layer for Kassia.
// Owns the spectral model, render engine, morph routine,
// and listener/callback system.
//
// The view registers callbacks here and calls controller methods
// in response to user input. The controller never touches the view directly.
//
// Requires: KassiaSpectralModel.sc, KassiaSynth.sc

KassiaController {

	var <model;       // KassiaSpectralModel
	var <synth;       // KassiaSynth
	var <presetBank;  // PresetBank
	var <bankPath;    // current bank file path for auto-save
	var listeners;    // Dictionary of Arrays of callbacks
	var morphRoutine; // active ratio morph routine, or nil

	*new { |model, synth|
		^super.new.init(model, synth)
	}

	init { |m, s|
		model      = m;
		synth      = s;
		listeners  = Dictionary.new;
		presetBank = PresetBank.new;
		bankPath   = nil;
		presetBank.onChanged({ |names| this.notify(\presets, names) });
		^this
	}

	// ------------------------------------------------------------------
	// Listener system
	// ------------------------------------------------------------------

	addListener { |key, func|
		listeners[key] = listeners[key].add(func);
	}

	removeListener { |key, func|
		if(func.isNil) {
			// Remove all listeners for this key
			listeners.removeAt(key);
		} {
			// Remove specific func only
			listeners[key] = listeners[key].select({ |f| f != func });
		};
	}

	notify { |key ...args|
		var fns = listeners[key];
		if(fns.notNil) { fns.do({ |f| f.valueArray(args) }) };
	}

	// ------------------------------------------------------------------
	// Spectral model parameters
	// These update the model, recompute partials, push to synth,
	// and notify listeners.
	// ------------------------------------------------------------------

	setCarrier { |hz|
		model.setCarrier(hz);
		synth.set(\root, model.carrier);
		this.prPushPartials;
		this.notify(\carrier,  model.carrier);
		this.notify(\modHz,    model.modHz);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	setRatio { |r|
		model.setRatio(r);
		this.prPushPartials;
		this.notify(\ratio,    model.ratio);
		this.notify(\modHz,    model.modHz);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	setIndex { |i|
		model.setIndex(i);
		this.prPushPartials;
		this.notify(\index,    model.index);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	setTilt { |t|
		model.setTilt(t);
		this.prPushPartials;
		this.notify(\tilt,     model.tilt);
		this.notify(\partials, model.absFreqs, model.amps);
	}

	// Recompute and push partials without changing parameters.
	// Useful for init and after preset load.
	refreshPartials {
		this.prPushPartials;
		this.notify(\refresh,
			model.carrier, model.ratio, model.index, model.tilt,
			model.modHz, model.absFreqs, model.amps);
	}

	// Reinitialise level sliders from FM amplitudes only
	// (ratios unchanged — mirrors old "Init levels" button)
	initLevels {
		this.prPushPartials;
		this.notify(\partials, model.absFreqs, model.amps);
	}

	// ------------------------------------------------------------------
	// Synth parameters
	// These pass through to the synth and notify listeners.
	// ------------------------------------------------------------------

	set { |key, val|
		synth.set(key, val);
		this.notify(key, val);
	}

	// Per-partial array param (pans, amRate, amDepth, fmRate, fmDepthCents)
	setPartialParam { |key, values|
		synth.setPartialParam(key, values);
		this.notify(key, values);
	}

	// Single value within a per-partial array
	setPartialParamAt { |key, index, val|
		synth.setPartialParamAt(key, index, val);
		this.notify(key, synth.getState(key));
	}

	// Randomise partial phases
	randomisePhases {
		var phases;
		phases = Array.fill(synth.num, { 1.0.rand });
		synth.setPartialParam(\phase, phases);
		this.notify(\phase, phases);
	}

	// ------------------------------------------------------------------
	// Ratio morphing
	// ------------------------------------------------------------------

	morphRatioTo { |targetRatio, dur=10.0, updatesPerSec=20|
		var startRatio, safeStart, safeTarget, steps, waitTime;

		this.stopMorph;

		safeTarget  = targetRatio.clip(0.125, 8.0);
		startRatio  = model.ratio.clip(0.125, 8.0);

		if(dur <= 0.0) {
			this.setRatio(safeTarget);
			^this
		};

		safeStart       = startRatio.max(0.000001);
		safeTarget      = safeTarget.max(0.000001);
		updatesPerSec   = updatesPerSec.clip(1, 40);
		steps           = (dur * updatesPerSec).round(1).asInteger.max(2);
		waitTime        = dur / (steps - 1);

		morphRoutine = Routine({
			steps.do { |i|
				var x, r;
				x = i / (steps - 1);
				r = safeStart * ((safeTarget / safeStart) ** x);
				{ this.setRatio(r) }.defer;
				waitTime.wait;
			};
			{ this.setRatio(safeTarget) }.defer;
			morphRoutine = nil;
		}).play(AppClock);
	}

	stopMorph {
		if(morphRoutine.notNil) {
			morphRoutine.stop;
			morphRoutine = nil;
		};
	}

	isMorphing { ^morphRoutine.notNil }

	// ------------------------------------------------------------------
	// Presets
	// ------------------------------------------------------------------

	savePreset { |name|
		var snap;
		snap = (
			carrier:      model.carrier,
			ratio:        model.ratio,
			index:        model.index,
			tilt:         model.tilt,
			levels:       synth.getState(\levels).copy,
			pans:         synth.getState(\pans).copy,
			phase:        synth.getState(\phase).copy,
			amRate:       synth.getState(\amRate).copy,
			amDepth:      synth.getState(\amDepth).copy,
			fmRate:       synth.getState(\fmRate).copy,
			fmDepthCents: synth.getState(\fmDepthCents).copy
		);
		presetBank.save(name, snap);
		if(bankPath.notNil) { presetBank.writeToFile(bankPath) };
		this.notify(\presets, presetBank.names);
	}

	loadPreset { |name|
		var snap;
		snap = presetBank.load(name);
		if(snap.isNil) { ("KassiaController: preset not found: " ++ name).warn; ^this };

		// Set model parameters directly — avoids per-setter recompute/push
		if(snap[\carrier].notNil) { model.carrier = snap[\carrier].asFloat.max(0.1) };
		if(snap[\ratio].notNil)   { model.ratio   = snap[\ratio].asFloat.clip(0.125, 8.0) };
		if(snap[\index].notNil)   { model.index   = snap[\index].asFloat.clip(0.0, 10.0) };
		if(snap[\tilt].notNil)    { model.tilt    = snap[\tilt].asFloat.clip(-1.0, 1.0) };
		model.compute;

		// Apply per-partial state — non-level params can go before refresh
		if(snap[\pans].notNil)         { synth.setPartialParam(\pans,         snap[\pans]) };
		if(snap[\phase].notNil)        { synth.setPartialParam(\phase,        snap[\phase]) };
		if(snap[\amRate].notNil)       { synth.setPartialParam(\amRate,       snap[\amRate]) };
		if(snap[\amDepth].notNil)      { synth.setPartialParam(\amDepth,      snap[\amDepth]) };
		if(snap[\fmRate].notNil)       { synth.setPartialParam(\fmRate,       snap[\fmRate]) };
		if(snap[\fmDepthCents].notNil) { synth.setPartialParam(\fmDepthCents, snap[\fmDepthCents]) };

		synth.set(\root, model.carrier);

		// Refresh first (which pushes ratios + model amps to levels),
		// THEN override levels from preset so they survive.
		this.refreshPartials;
		if(snap[\levels].notNil) {
			synth.setPartialParam(\levels, snap[\levels]);
			this.notify(\partials, model.absFreqs, snap[\levels]);
		};

		this.notify(\presetLoaded, name);
	}

	deletePreset { |name|
		presetBank.delete(name);
		if(bankPath.notNil and: { presetBank.size > 0 }) {
			presetBank.writeToFile(bankPath);
		} {
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
	// Playback
	// ------------------------------------------------------------------

	play { |server, root=55, master=0.12|
		synth.play(server, root, master);
		this.refreshPartials;
	}

	free {
		this.stopMorph;
		synth.free;
	}

	// ------------------------------------------------------------------
	// Private
	// ------------------------------------------------------------------

	prPushPartials {
		synth.setPartials(model.freqs, model.amps);
	}

}
