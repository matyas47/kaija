// KaijaSpectralModel.sc
// Spectral model for Kaija.
// Subclass of KassiaSpectralModel — identical FM partial derivation and
// spectral tilt, but applies RMS normalisation instead of peak normalisation.
//
// RMS normalisation keeps perceived loudness consistent across different
// ratio/index/tilt combinations, which is important for a performance synth
// where timbral changes should not cause level jumps.
//
// Requires: KassiaSpectralModel.sc

KaijaSpectralModel : KassiaSpectralModel {

    // RMS-normalise: scale amplitudes so their RMS matches a target level.
    // Target is set such that 8 sine partials at uniform amplitude sum to
    // a peak around 1.0. Unlike peak normalisation, RMS keeps perceived
    // loudness consistent across changes in spectral content (ratio, index,
    // tilt) — important for a performance synth where timbral changes
    // should not cause level jumps.
    prNormalise { |rawAmps|
        var rms, target = 0.35;
        rms = ((rawAmps.collect({ |a| a * a }).sum / numPartials) ** 0.5).max(1e-12);
        ^(rawAmps * (target / rms)).collect(_.asFloat)
    }

}
