// KaijaSynth.sc
// Single-voice render engine for Kaija.
// Subclass of KassiaSynth — inherits additive FM-partial synthesis,
// formant filtering, and MoogFF, but replaces the drone-oriented
// control surface with note-event / ADSR / velocity logic.
//
// Stripped from KassiaSynth:
//   - per-partial pan (all voices centred)
//   - per-partial fmRate / fmDepthCents
//   - filterMod LFO
//
// Added:
//   - ADSR envelope controlling both amplitude and VCF cutoff
//   - velocity scaling on amplitude
//   - gate argument for note-on/off
//
// One KaijaSynth instance = one voice.
// The controller owns a pool of these.
//
// Signal flow: partials -> AM -> drive -> formant -> MoogFF(+ADSR) -> ADSR amp -> limiter
//
// Requires: KassiaSynth.sc, KassiaFormant.sc

KaijaSynth : KassiaSynth {

	classvar <>defName = \kaija_core;

	// Override init — reduced state (no pans, fmRate, fmDepthCents)
	init { |n, pg|
		num         = n.asInteger.max(1);
		partialGain = pg.asFloat;
		state = (
			ratios:  (1..num).collect(_.asFloat),
			levels:  Array.fill(num, 0.0),
			phase:   Array.fill(num, 0.0),
			amRate:  Array.fill(num, 0.05),
			amDepth: Array.fill(num, 0.0)
		);
		^this
	}

	// ------------------------------------------------------------------
	// SynthDef registration
	// ------------------------------------------------------------------

	*addDef { |num=8, defName=\kaija_core|
		var n;
		n = num.asInteger.max(1);

		SynthDef(defName, { |out=0, gate=1,
			root=55, master=0.15, velocity=1.0,
			pitchBend=0.0,        // pitch bend in semitones (+/-)
			vcfFreq=2000, vcfRQ=0.35,
			vcfEnvAmt=0.5,        // how much the ADSR opens the filter (0–1)
			vcfLFORate=0.5, vcfLFODepth=0.0,  // VCF LFO
			partLPF=6000, partLPFRQ=0.5,
			vowelMix=0.0, vowelPos=1.5, vowelRQ=1.0,
			noiseAmp=0.0,         // pink noise mix level (0–1)
			// ADSR
			attack=0.01, decay=0.3, sustain=0.7, release=0.5,
			// smoothing lag times
			masterLag=0.05, vcfLag=0.3, vcfRQLag=0.3,
			driveLag=0.1, partLevelLag=0.1, amRateLag=0.5, amDepthLag=0.5,
			ratioLag=0.3, noiseLag=0.1,
			drive=1.0, drivePost=1.0
			|

			var ratios, levels, phase, amRate, amDepth;
			var env, vcfLFO, vcfCut, baseF, f, ph, osc, am, per, sig, noise;
			var vmix, vpos, vrq, mstr, vcfFreqSm, vcfRQSm, driveSm;
			var levelsSm, amRateSm, amDepthSm, bendRatio, noiseAmpSm;

			ratios  = NamedControl.kr(\ratios,  (1..n));
			levels  = NamedControl.kr(\levels,  Array.fill(n, 0.0));
			phase   = NamedControl.kr(\phase,   Array.fill(n, 0.0));
			amRate  = NamedControl.kr(\amRate,  Array.fill(n, 0.05));
			amDepth = NamedControl.kr(\amDepth, Array.fill(n, 0.0));

			// ADSR envelope — controls both amplitude and filter
			env = EnvGen.kr(
				Env.adsr(attack, decay, sustain, release),
				gate,
				doneAction: 2
			);

			mstr       = Lag.kr(master,                   masterLag.max(0.001));
			vcfFreqSm  = Lag.kr(vcfFreq.clip(20, 20000),  vcfLag.max(0.001));
			vcfRQSm    = Lag.kr(vcfRQ.clip(0.05, 0.95),   vcfRQLag.max(0.001));
			driveSm    = Lag.kr(drive.clip(0.25, 8.0),    driveLag.max(0.001));
			levelsSm   = Lag.kr(levels,                    partLevelLag.max(0.001));
			amRateSm   = Lag.kr(amRate.clip(0.0001, 2.0), amRateLag.max(0.001));
			amDepthSm  = Lag.kr(amDepth.clip(0.0, 1.0),   amDepthLag.max(0.001));
			noiseAmpSm = Lag.kr(noiseAmp.clip(0.0, 1.0),  noiseLag.max(0.001));

			// Pitch bend: semitones -> ratio
			bendRatio = pitchBend.midiratio;

			// VCF LFO + ADSR envelope modulation
			vcfLFO = SinOsc.kr(vcfLFORate.max(0.0001)).range(
				1 - vcfLFODepth, 1 + vcfLFODepth
			);
			vcfCut = (vcfFreqSm * vcfLFO * (1 + (env * vcfEnvAmt * 3))).clip(20, 20000);

			// Partial frequencies — pitch bend and ratio smoothing applied
			baseF = (root * bendRatio * Lag.kr(ratios, ratioLag.max(0.001))).clip(0.1, 20000);
			f     = baseF;

			ph  = phase.wrap(0, 1);
			osc = VarSaw.ar(f, iphase: ph, width: 0.5);
			osc = RLPF.ar(osc, partLPF.clip(50, 20000), partLPFRQ.clip(0.05, 1.0));

			// Per-partial AM
			am  = SinOsc.kr(amRateSm.max(0.000001)).range(1 - amDepthSm, 1).clip(0, 1);
			per = osc * am * levelsSm;
			sig = Mix(per);

			// Pink noise mixed in parallel before drive/filter chain
			noise = PinkNoise.ar(noiseAmpSm);
			sig   = sig + noise;

			sig = (sig * driveSm).tanh * drivePost;

			vmix = Lag.kr(vowelMix.clip(0, 1),    0.1);
			vpos = Lag.kr(vowelPos.clip(0, 4),     0.1);
			vrq  = Lag.kr(vowelRQ.clip(0.5, 3.0),  0.1);

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

			// Amplitude envelope × velocity × master
			sig = sig * env * velocity * mstr;
			sig = Limiter.ar(sig, 0.95, 0.01);

			// Output as stereo (centred)
			Out.ar(out, sig ! 2);
		}).add;
	}

	// ------------------------------------------------------------------
	// Note event API
	// ------------------------------------------------------------------

	// Start a note. Called by the controller on MIDI note-on.
	// freq:     frequency in Hz (from scale lookup)
	// velocity: normalised 0–1
	// server:   SC server
	noteOn { |freq, velocity=1.0, master=0.15, server|
		// Apply square root curve so soft notes remain audible
		var scaledVel = velocity.sqrt;
		server = server ? Server.default;
		node = Synth(defName, [
			\root,     freq,
			\velocity, scaledVel,
			\master,   0.0,
			\gate,     1
		], server);
		this.prPushAll;
		SystemClock.sched(0.05, { node.set(\master, master); nil });
	}

	// Release a note normally — goes through release envelope.
	noteOff {
		if(node.notNil) { node.set(\gate, 0) };
		// node will free itself via doneAction: 2 when envelope completes
		node = nil;
	}

	// Hard kill — frees node immediately, bypasses release envelope.
	// Used by MIDI flush.
	freeSynth {
		if(node.notNil) { node.free };
		node = nil;
	}

	// ------------------------------------------------------------------
	// Override prPushAll for reduced state
	// ------------------------------------------------------------------

	prPushAll {
		if(node.notNil) {
			node.setn(\ratios,  state[\ratios]);
			node.setn(\levels,  state[\levels]);
			node.setn(\phase,   state[\phase]);
			node.setn(\amRate,  state[\amRate]);
			node.setn(\amDepth, state[\amDepth]);
		};
	}

}
