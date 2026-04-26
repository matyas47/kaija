// KassiaSynth.sc
// Render engine for Kassia (and base class for KaijaSynth).
// Owns the SC server node, SynthDef, and per-partial state.
// Callers interact through the public API only — internal state
// and push mechanics are not exposed.
//
// Signal flow: partials -> drive -> formant -> MoogFF -> limiter
//
// Requires: KassiaFormant.sc

KassiaSynth {

	classvar <>defName = \kassia_core;

	var <>node;
	var <>num;
	var <>partialGain;  // master scaling applied to normalised 0–1 amplitudes

	var state;          // internal — use set/setPartials to modify

	*new { |num=8, partialGain=0.14|
		^super.new.init(num, partialGain)
	}

	init { |n, pg|
		num         = n.asInteger.max(1);
		partialGain = pg.asFloat;
		state = (
			ratios:       (1..num).collect(_.asFloat),
			detuneCents:  Array.fill(num, 0.0),
			levels:       Array.fill(num, 0.0),
			pans:         Array.fill(num, 0.0),
			phase:        Array.fill(num, 0.0),
			amRate:       Array.fill(num, 0.05),
			amDepth:      Array.fill(num, 0.2),
			fmRate:       Array.fill(num, 0.03),
			fmDepthCents: Array.fill(num, 0.0)
		);
		^this
	}

	// ------------------------------------------------------------------
	// SynthDef registration
	// ------------------------------------------------------------------

	*addDef { |num=8, defName=\kassia_core|
		var n;
		n = num.asInteger.max(1);

		SynthDef(defName, { |out=0, gate=1,
			root=55, master=0.15,
			vcfFreq=1200, vcfRQ=0.35,
			vcfModRate=0.07, vcfModDepth=0.25,
			partLPF=6000, partLPFRQ=0.5,
			vowelMix=0.35, vowelPos=1.5, vowelRQ=1.0,
			// smoothing lag times
			masterLag=0.5, vcfLag=1.5, vcfRQLag=1.0, driveLag=1.0,
			partLevelLag=2.0, panLag=1.0, fmDepthLag=2.0,
			amRateLag=2.0, amDepthLag=2.0, fmRateLag=2.0,
			ratioLag=0.5, detuneLag=0.5,
			drive=1.0, drivePost=1.0,
			filterModRate=0.03, filterModDepth=0.0
			|

			var ratios, detuneCents, levels, pans, phase, amRate, amDepth, fmRate, fmDepthCents;
			var ratiosSm, detuneSm;
			var env, vcfLFO, vcfCut, baseF, fm, f, ph, osc, am, per, sig;
			var vmix, vpos, vrq, mstr, vcfFreqSm, vcfRQSm, driveSm;
			var levelsSm, pansSm, fmDepthSm, amRateSm, amDepthSm, fmRateSm;

			ratios       = NamedControl.kr(\ratios,       (1..n));
			detuneCents  = NamedControl.kr(\detuneCents,  Array.fill(n, 0));
			levels       = NamedControl.kr(\levels,       Array.fill(n, 0.0));
			pans         = NamedControl.kr(\pans,         Array.fill(n, 0.0));
			phase        = NamedControl.kr(\phase,        Array.fill(n, 0.0));
			amRate       = NamedControl.kr(\amRate,       Array.fill(n, 0.05));
			amDepth      = NamedControl.kr(\amDepth,      Array.fill(n, 0.2));
			fmRate       = NamedControl.kr(\fmRate,       Array.fill(n, 0.03));
			fmDepthCents = NamedControl.kr(\fmDepthCents, Array.fill(n, 0.0));

			env = EnvGen.kr(Env.asr(2.0, 1.0, 3.0), gate, doneAction: 2);

			mstr      = Lag.kr(master,                    masterLag.max(0.001));
			vcfFreqSm = Lag.kr(vcfFreq.clip(20, 20000),  vcfLag.max(0.001));
			vcfRQSm   = Lag.kr(vcfRQ.clip(0.05, 0.95),   vcfRQLag.max(0.001));
			driveSm   = Lag.kr(drive.clip(0.25, 8.0),    driveLag.max(0.001));
			levelsSm  = Lag.kr(levels,                    partLevelLag.max(0.001));
			pansSm    = Lag.kr(pans,                      panLag.max(0.001));
			fmDepthSm = Lag.kr(fmDepthCents,              fmDepthLag.max(0.001));
			amRateSm  = Lag.kr(amRate.clip(0.0001, 2.0), amRateLag.max(0.001));
			amDepthSm = Lag.kr(amDepth.clip(0.0, 1.0),   amDepthLag.max(0.001));
			fmRateSm  = Lag.kr(fmRate.clip(0.0001, 2.0), fmRateLag.max(0.001));
			ratiosSm  = Lag.kr(ratios,                    ratioLag.max(0.001));
			detuneSm  = Lag.kr(detuneCents,               detuneLag.max(0.001));

			vcfLFO = SinOsc.kr(filterModRate).range(-1, 1);
			vcfCut = (vcfFreqSm * (vcfLFO * filterModDepth + 1)).clip(20, 20000);

			baseF = (root * ratiosSm * (detuneSm / 100).midiratio).clip(0.1, 20000);
			fm    = SinOsc.kr(fmRateSm.max(0.000001)).bipolar(fmDepthSm / 100).midiratio;
			f     = (baseF * fm).clip(0.1, 20000);

			ph  = phase.wrap(0, 1);
			osc = VarSaw.ar(f, iphase: ph, width: 0.5);
			osc = RLPF.ar(osc, partLPF.clip(50, 20000), partLPFRQ.clip(0.05, 1.0));

			am  = SinOsc.kr(amRateSm.max(0.000001)).range(1 - amDepthSm, 1).clip(0, 1);
			per = Pan2.ar(osc * am * levelsSm, pansSm);
			sig = Mix(per);

			sig = (sig * driveSm).tanh * drivePost;

			vmix = Lag.kr(vowelMix.clip(0, 1),        2.5);
			vpos = Lag.kr(vowelPos.clip(0, 4),         3.5);
			vrq  = Lag.kr(vowelRQ.clip(0.5, 3.0),      1.5);

			sig = FormantVowel.process(
				sig,
				rootHz:   root,
				mix:      vmix,
				pos:      vpos,
				rq:       vrq,
				scaleRef: 110
			);

			sig = MoogFF.ar(sig, vcfCut, vcfRQSm.linlin(0, 1, 0.0, 4.0));
			sig = LeakDC.ar(sig);
			sig = sig * mstr * env;
			sig = Limiter.ar(sig, 0.95, 0.01);

			Out.ar(out, sig);
		}).add;
	}

	// ------------------------------------------------------------------
	// Playback
	// ------------------------------------------------------------------

	play { |server, root=55, master=0.12|
		server = server ? Server.default;
		// Start with all state pre-loaded as Synth args — no post-creation push needed.
		// The 2s ASR attack envelope handles the smooth onset.
		node = Synth(defName, [
			\root,        root,
			\master,      master,
			\ratios,      state[\ratios],
			\detuneCents, state[\detuneCents],
			\levels,      state[\levels],
			\pans,        state[\pans],
			\phase,       state[\phase],
			\amRate,      state[\amRate],
			\amDepth,     state[\amDepth],
			\fmRate,      state[\fmRate],
			\fmDepthCents, state[\fmDepthCents]
		], server);
		^node
	}

	free {
		var n = node;
		node = nil;
		// gate=0 triggers the env release; doneAction:2 frees the node
		if(n.notNil) { n.set(\gate, 0) };
	}

	// ------------------------------------------------------------------
	// Public API
	// ------------------------------------------------------------------

	// Set a single synth parameter directly (vcfFreq, drive, vowelMix, etc.)
	set { |key, val|
		if(node.notNil) { node.set(key, val) };
	}

	// Push computed partials from the spectral model.
	// freqs: Array of ratios relative to root (0–n)
	// amps:  Array of normalised amplitudes (0–1)
	// partialGain is applied here so the model stays clean.
	setPartials { |freqs, amps|
		state[\ratios] = freqs.collect(_.asFloat);
		state[\levels] = amps.collect({ |a| (a * partialGain).asFloat });
		if(node.notNil) {
			node.setn(\ratios, state[\ratios]);
			node.setn(\levels, state[\levels]);
		};
	}

	// Set a per-partial array (pans, amRate, amDepth, fmRate, fmDepthCents, phase)
	setPartialParam { |key, values|
		state[key] = values.collect(_.asFloat);
		if(node.notNil) { node.setn(key, state[key]) };
	}

	// Set a single value within a per-partial array.
	// For \levels, val is expected as normalised 0–1; partialGain is applied here.
	setPartialParamAt { |key, index, val|
		var scaled;
		scaled = if(key == \levels) { val * partialGain } { val };
		state[key][index] = scaled.asFloat;
		if(node.notNil) { node.setn(key, state[key]) };
	}

	// Read back current state for a key (e.g. for UI restore after rebuild)
	getState { |key| ^state[key] }

	// ------------------------------------------------------------------
	// Private
	// ------------------------------------------------------------------

	prPushAll {
		if(node.notNil) {
			node.setn(\ratios,       state[\ratios]);
			node.setn(\detuneCents,  state[\detuneCents]);
			node.setn(\levels,       state[\levels]);
			node.setn(\pans,         state[\pans]);
			node.setn(\phase,        state[\phase]);
			node.setn(\amRate,       state[\amRate]);
			node.setn(\amDepth,      state[\amDepth]);
			node.setn(\fmRate,       state[\fmRate]);
			node.setn(\fmDepthCents, state[\fmDepthCents]);
		};
	}

}
