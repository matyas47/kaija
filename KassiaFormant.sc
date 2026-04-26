// KassiaFormant.sc
// Revised for per-formant bandwidth accuracy.
//
// Changes from previous version:
// - Each formant band now uses an individually derived RQ based on
//   measured vowel bandwidths (Hz), scaled by rootHz tracking.
// - The rq argument becomes a global bandwidth multiplier:
//   1.0 = natural bandwidths, >1 = wider/smoother, <1 = tighter/more resonant.
// - Vowel positions: 0=A, 1=E, 2=I, 3=O, 4=U  (same as before)

FormantVowel {

	*process { |in, rootHz=110, mix=0.35, pos=1.5, rq=1.0, scaleRef=110|

		var vPos, vMix, vScale;
		var f1, f2, f3, f4;
		var bw1, bw2, bw3, bw4;
		var rq1, rq2, rq3, rq4;
		var formant;

		vPos   = pos.clip(0, 4);
		vMix   = mix.clip(0, 1);
		vScale = (rootHz / scaleRef).clip(0.25, 4.0);

		// --- formant centre frequencies (Hz, unscaled) ---
		// Columns: A,    E,    I,    O,    U
		f1 = SelectX.kr(vPos, [800,  400,  300,  450,  325]) * vScale;
		f2 = SelectX.kr(vPos, [1150, 1700, 2200, 800,  700]) * vScale;
		f3 = SelectX.kr(vPos, [2900, 2600, 3000, 2830, 2530]) * vScale;
		f4 = SelectX.kr(vPos, [3900, 3200, 3600, 3800, 3500]) * vScale;

		f1 = f1.clip(20, 20000);
		f2 = f2.clip(20, 20000);
		f3 = f3.clip(20, 20000);
		f4 = f4.clip(20, 20000);

		// --- per-formant bandwidths (Hz, unscaled) ---
		// Based on Peterson & Barney / Fant reference values.
		// F1 is broadest, F3/F4 are narrowest.
		// Columns: A,   E,   I,   O,   U
		bw1 = SelectX.kr(vPos, [70,  60,  60,  70,  60]) * vScale;
		bw2 = SelectX.kr(vPos, [80,  90,  90,  80,  80]) * vScale;
		bw3 = SelectX.kr(vPos, [120, 100, 100, 110, 100]) * vScale;
		bw4 = SelectX.kr(vPos, [300, 250, 250, 300, 270]) * vScale;

		// rq = bandwidth / centre frequency; scaled by the global rq multiplier
		rq1 = ((bw1 / f1) * rq).clip(0.01, 2.0);
		rq2 = ((bw2 / f2) * rq).clip(0.01, 2.0);
		rq3 = ((bw3 / f3) * rq).clip(0.01, 2.0);
		rq4 = ((bw4 / f4) * rq).clip(0.01, 2.0);

		formant =
			(BPF.ar(in, f1, rq1) * 1.00) +
			(BPF.ar(in, f2, rq2) * 0.85) +
			(BPF.ar(in, f3, rq3) * 0.65) +
			(BPF.ar(in, f4, rq4) * 0.50);

		// Slight makeup gain so the wet branch is audible at moderate mix
		formant = formant * 1.35;

		^XFade2.ar(in, formant, vMix.linlin(0, 1, -1, 1));
	}
}
