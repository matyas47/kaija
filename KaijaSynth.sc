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

	// Override init — reduced state (no pans, fmRate, fmDepthCents, amRate, amDepth)
	// Kaija also skips partialGain scaling — RMS normalisation already produces
	// usable amplitudes, and direct correspondence between slider and synth
	// makes presets and UI behaviour unambiguous.
	init { |n, pg|
		num         = n.asInteger.max(1);
		partialGain = 1.0;   // forced to unity in Kaija; pg arg ignored
		state = (
			ratios:  (1..num).collect(_.asFloat),
			levels:  Array.fill(num, 0.0),
			phase:   Array.fill(num, 0.0),
			partAtk: Array.fill(num, 0.0),
			partRel: Array.fill(num, 0.0)
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
			root=55, master=0.5, velocity=1.0,
			pitchBend=0.0,
			vcfFreq=2000, vcfRQ=0.35,
			vcfEnvAmt=0.5,
			vcfLFORate=0.5, vcfLFODepth=0.0,
			partLPF=6000, partLPFRQ=0.5,
			vowelPos=1.5,
			noiseAmp=0.0,
			// ADSR
			attack=0.01, decay=0.3, sustain=0.7, release=0.5,
			// smoothing lag times
			masterLag=0.05, vcfLag=0.3, vcfRQLag=0.3,
			driveLag=0.1, partLevelLag=0.1,
			ratioLag=0.3, noiseLag=0.1,
			drive=1.0, drivePost=1.0
			|

			var ratios, levels, phase, partAtk, partRel;
			var env, partEnvs, vcfLFO, vcfCut, baseF, f, ph, osc, per, sig, noise;
			var vpos, mstr, vcfFreqSm, vcfRQSm, driveSm;
			var levelsSm, noiseAmpSm, bendRatio;

			ratios  = NamedControl.kr(\ratios,  (1..n));
			levels  = NamedControl.kr(\levels,  Array.fill(n, 0.0));
			phase   = NamedControl.kr(\phase,   Array.fill(n, 0.0));
			partAtk = NamedControl.kr(\partAtk, Array.fill(n, 0.0));
			partRel = NamedControl.kr(\partRel, Array.fill(n, 0.0));

			// Main ADSR — overall amplitude and filter shape
			env = EnvGen.kr(
				Env.adsr(attack, decay, sustain, release),
				gate,
				doneAction: 2
			);

			// Per-partial ASR envelopes — shape timbre independently of main ADSR
			// sustain is 1.0; level slider controls steady-state amplitude
			partEnvs = Array.fill(n, { |i|
				EnvGen.kr(
					Env.asr(
						partAtk[i].max(0.0001),
						1.0,
						partRel[i].max(0.0001)
					),
					gate
				)
			});

			mstr       = Lag.kr(master,                   masterLag.max(0.001));
			vcfFreqSm  = Lag.kr(vcfFreq.clip(20, 20000),  vcfLag.max(0.001));
			vcfRQSm    = Lag.kr(vcfRQ.clip(0.05, 0.95),   vcfRQLag.max(0.001));
			driveSm    = Lag.kr(drive.clip(0.25, 8.0),    driveLag.max(0.001));
			levelsSm   = Lag.kr(levels,                    partLevelLag.max(0.001));
			noiseAmpSm = Lag.kr(noiseAmp.clip(0.0, 1.0),  noiseLag.max(0.001));

			// Pitch bend: semitones -> ratio
			bendRatio = pitchBend.midiratio;

			// VCF LFO + ADSR envelope modulation
			// env squared gives gentler filter opening at note onset
			vcfLFO = SinOsc.kr(vcfLFORate.max(0.0001)).range(
				1 - vcfLFODepth, 1 + vcfLFODepth
			);
			vcfCut = (vcfFreqSm * vcfLFO * (1 + ((env ** 2) * vcfEnvAmt * 2))).clip(20, 20000);

			// Partial frequencies — pitch bend and ratio smoothing applied
			baseF = (root * bendRatio * Lag.kr(ratios, ratioLag.max(0.001))).clip(0.1, 20000);
			f     = baseF;

			ph  = phase.wrap(0, 1);
			osc = VarSaw.ar(f, iphase: ph, width: 0.5);
			osc = RLPF.ar(osc, partLPF.clip(50, 20000), partLPFRQ.clip(0.05, 1.0));

			// Per-partial ASR × level → mix
			per = osc * partEnvs * levelsSm;
			sig = Mix(per);

			// Pink noise mixed in parallel before drive/filter chain
			noise = PinkNoise.ar(noiseAmpSm);
			sig   = sig + noise;

			sig = (sig * driveSm).tanh * drivePost;

			vpos = Lag.kr(vowelPos.clip(0, 4), 0.1);

			sig = FormantVowel.process(
				sig,
				rootHz:   root,
				mix:      1.0,
				pos:      vpos,
				rq:       1.0,
				scaleRef: 110
			);

			sig = MoogFF.ar(sig, vcfCut, vcfRQSm.linlin(0, 1, 0.0, 4.0));
			sig = LeakDC.ar(sig);

			// Main ADSR × velocity × master
			sig = sig * env * velocity * mstr;
			sig = Limiter.ar(sig, 0.99, 0.01);

			Out.ar(out, sig ! 2);
		}).add;
	}

	// ------------------------------------------------------------------
	// Note event API
	// ------------------------------------------------------------------

	// Start a note with a pre-built args array.
	// Used by KaijaController to bundle all per-note parameters into a single
	// OSC message at Synth creation, avoiding post-creation `set` calls.
	noteOnWithArgs { |args, server|
		server = server ? Server.default;
		node = Synth(defName, args, server);
	}

	// Legacy single-note entry point — kept for potential external callers.
	// New code should prefer noteOnWithArgs for performance.
	noteOn { |freq, velocity=1.0, master=0.5, server|
		var scaledVel = velocity.sqrt;
		server = server ? Server.default;
		node = Synth(defName, [
			\root,     freq,
			\velocity, scaledVel,
			\master,   master,
			\gate,     1,
			\ratios,   state[\ratios],
			\levels,   state[\levels],
			\phase,    state[\phase],
			\partAtk,  state[\partAtk],
			\partRel,  state[\partRel]
		], server);
	}

	// Release a note — sends gate=0 to trigger the release envelope,
	// then immediately nils the node reference.
	// Note: the server-side node continues running through the release tail,
	// but nilling node here frees this voice for reallocation. This means
	// the voice activity light goes dark before the release actually finishes,
	// and in extreme cases (8 fast notes) a new note could steal a releasing
	// voice. Both are acceptable trade-offs for an 8-voice polyphonic synth.
	// doneAction:2 in the SynthDef ensures the server node frees itself.
	noteOff {
		if(node.notNil) { node.set(\gate, 0) };
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
			node.setn(\partAtk, state[\partAtk]);
			node.setn(\partRel, state[\partRel]);
		};
	}

}
