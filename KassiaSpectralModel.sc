// KassiaSpectralModel.sc
// Spectral model for Kassia and Kaija.
// Pure logic — no server, no GUI.
// Computes FM-derived partial frequencies and amplitudes
// from carrier, ratio, index, and tilt parameters.
//
// Amplitudes are normalised to 0–1 after tilt is applied.
// Scaling to synth gain levels is the render engine's responsibility.

KassiaSpectralModel {

    var <>carrier;      // root/carrier frequency in Hz
    var <>ratio;        // FM modulator ratio
    var <>index;        // FM index
    var <>numPartials;  // number of partials to use
    var <>tilt;         // spectral tilt: 0.0 = neutral, >0 = brighter, <0 = darker
    var <>density;      // partial density/pruning threshold (future — 0.0 = all partials)

    var <freqs;         // computed partial frequencies, as ratios relative to carrier
    var <amps;          // computed partial amplitudes, normalised 0–1

    *new { |carrier=55, ratio=1.0, index=3.0, numPartials=8, tilt=0.0|
        ^super.new.init(carrier, ratio, index, numPartials, tilt)
    }

    init { |c, r, i, n, t|
        carrier     = c.asFloat;
        ratio       = r.asFloat.clip(0.125, 8.0);
        index       = i.asFloat.clip(0.0, 10.0);
        numPartials = n.asInteger.max(1);
        tilt        = t.asFloat.clip(-1.0, 1.0);
        density     = 0.0;
        freqs       = Array.newClear(numPartials);
        amps        = Array.newClear(numPartials);
        this.compute;
    }

    // Recompute partials from current parameters.
    // Call this after changing any parameter directly.
    compute {
        var obj, rawFreqs, rawAmps, mx;

        carrier = carrier.max(0.1);
        ratio   = ratio.clip(0.125, 8.0);
        index   = index.clip(0.0, 10.0);
        tilt    = tilt.clip(-1.0, 1.0);

        obj      = FMRatioPartials.new(carrier, ratio, index, 200);
        rawFreqs = obj.freqs.asArray.collect(_.asFloat).copyRange(0, numPartials - 1);
        rawAmps  = obj.amps.asArray.collect({ |a| a.asFloat.abs }).copyRange(0, numPartials - 1);

        // Apply spectral tilt as a power law across partial index.
        // Each partial is weighted by (partialNumber ** tilt), shifting
        // the spectral centroid up (tilt > 0) or down (tilt < 0).
        if(tilt != 0.0) {
            rawAmps = rawAmps.collect({ |a, i|
                a * ((i + 1).asFloat ** tilt)
            });
        };

        // Future hook: density/pruning
        // rawAmps = this.applyDensity(rawAmps);

        // Normalise amplitudes to 0–1 after tilt
        mx      = rawAmps.maxItem.max(1e-12);
        rawAmps = rawAmps / mx;

        // Store frequencies as ratios relative to carrier
        freqs = rawFreqs.collect({ |f| (f / carrier).asFloat });
        amps  = rawAmps.collect(_.asFloat);
    }

    // Convenience setters — update parameter and recompute
    setCarrier { |hz|
        carrier = hz.asFloat.max(0.1);
        this.compute;
    }

    setRatio { |r|
        ratio = r.asFloat.clip(0.125, 8.0);
        this.compute;
    }

    setIndex { |i|
        index = i.asFloat.clip(0.0, 10.0);
        this.compute;
    }

    setTilt { |t|
        tilt = t.asFloat.clip(-1.0, 1.0);
        this.compute;
    }

    // Modulator frequency in Hz (for display)
    modHz {
        ^(carrier * ratio)
    }

    // Absolute partial frequencies in Hz (freqs are stored as ratios)
    absFreqs {
        ^freqs.collect({ |r| (r * carrier).asFloat })
    }

}
