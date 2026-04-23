// KaijaSpectralModel.sc
// Spectral model for Kaija.
// Subclass of KassiaSpectralModel — identical FM partial derivation,
// with tilt as a primary parameter and RMS normalisation applied after
// tilt so energy stays consistent across parameter changes.
//
// RMS normalisation is important for a performance instrument: without it,
// different ratio/index/tilt combinations produce very different loudness,
// which is disruptive when playing melodically.
//
// Amplitudes are normalised to 0–1 (RMS-consistent).
// Scaling to synth gain levels is the render engine's responsibility.
//
// Requires: KassiaSpectralModel.sc

KaijaSpectralModel : KassiaSpectralModel {

	// Override compute to add RMS normalisation after tilt.
	compute {
		var obj, rawFreqs, rawAmps, mx, rms;

		carrier = carrier.max(0.1);
		ratio   = ratio.clip(0.125, 8.0);
		index   = index.clip(0.0, 10.0);
		tilt    = tilt.clip(-1.0, 1.0);

		obj      = FMRatioPartials.new(carrier, ratio, index, 200);
		rawFreqs = obj.freqs.asArray.collect(_.asFloat).copyRange(0, numPartials - 1);
		rawAmps  = obj.amps.asArray.collect({ |a| a.asFloat.abs }).copyRange(0, numPartials - 1);

		// Apply spectral tilt as a power law across partial index
		if(tilt != 0.0) {
			rawAmps = rawAmps.collect({ |a, i|
				a * ((i + 1).asFloat ** tilt)
			});
		};

		// Peak normalise first so tilt doesn't cause clipping
		mx      = rawAmps.maxItem.max(1e-12);
		rawAmps = rawAmps / mx;

		// RMS normalise — scale so RMS of the partial array equals 1.0,
		// keeping perceived loudness consistent across parameter changes.
		rms = ((rawAmps.collect({ |a| a * a }).sum / numPartials) ** 0.5).max(1e-12);
		rawAmps = (rawAmps * (1.0 / rms)).clip(0, 1);

		// Store frequencies as ratios relative to carrier
		freqs = rawFreqs.collect({ |f| (f / carrier).asFloat });
		amps  = rawAmps.collect(_.asFloat);
	}

}
