// KassiaSpectralModel.sc
// Spectral model for Kassia and Kaija.
// Pure logic — no server, no GUI.
// Computes FM-derived partial frequencies and amplitudes
// from carrier, ratio, index, and tilt parameters.
//
// Subclasses override prNormalise to apply different amplitude
// normalisation strategies (peak normalise vs RMS normalise).
//
// Requires: FMRatioPartials.sc

KassiaSpectralModel {

    var <>carrier;      // root/carrier frequency in Hz
    var <>ratio;        // FM modulator ratio
    var <>index;        // FM index
    var <>numPartials;  // number of partials to use
    var <>tilt;         // spectral tilt: 0.0 = neutral, >0 = brighter, <0 = darker

    var <freqs;         // computed partial ratios relative to carrier
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
        freqs       = Array.newClear(numPartials);
        amps        = Array.newClear(numPartials);
        this.compute;
    }

    // Recompute partials from current parameters.
    // Shared pipeline: FM -> tilt -> subclass normalisation.
    compute {
        var rawFreqs, rawAmps, raw;

        carrier = carrier.max(0.1);
        ratio   = ratio.clip(0.125, 8.0);
        index   = index.clip(0.0, 10.0);
        tilt    = tilt.clip(-1.0, 1.0);

        // Derive partials from FM spectrum
        raw      = this.prComputeRaw;
        rawFreqs = raw[0];
        rawAmps  = raw[1];

        // Spectral tilt — power law weighting across partial index
        if(tilt != 0.0) {
            rawAmps = rawAmps.collect({ |a, i|
                a * ((i + 1).asFloat ** tilt)
            });
        };

        // Subclass hook: normalise amplitudes to 0–1
        amps  = this.prNormalise(rawAmps);
        freqs = rawFreqs.collect({ |f| (f / carrier).asFloat });
    }

    // Convenience setters — update parameter and recompute
    setCarrier { |hz|  carrier = hz.asFloat.max(0.1);        this.compute }
    setRatio   { |r|   ratio   = r.asFloat.clip(0.125, 8.0); this.compute }
    setIndex   { |i|   index   = i.asFloat.clip(0.0, 10.0);  this.compute }
    setTilt    { |t|   tilt    = t.asFloat.clip(-1.0, 1.0);  this.compute }

    // Modulator frequency in Hz (for display)
    modHz { ^(carrier * ratio) }

    // Absolute partial frequencies in Hz
    absFreqs { ^freqs.collect({ |r| (r * carrier).asFloat }) }

    // ------------------------------------------------------------------
    // Private — override in subclasses for different normalisation
    // ------------------------------------------------------------------

    // Compute raw FM-derived [freqs, amps] arrays before tilt/normalisation.
    // FMRatioPartials may return fewer than numPartials entries when index is
    // very small (only the carrier survives). Pad missing slots with carrier
    // frequency and zero amplitude so downstream code always sees full arrays.
    prComputeRaw {
        var obj, allFreqs, allAmps, rawFreqs, rawAmps, n;
        obj      = FMRatioPartials.new(carrier, ratio, index, 200);
        allFreqs = obj.freqs.asArray.collect(_.asFloat);
        allAmps  = obj.amps.asArray.collect({ |a| a.asFloat.abs });
        n        = allFreqs.size;
        rawFreqs = Array.fill(numPartials, { |i|
            if(i < n) { allFreqs[i] } { carrier }
        });
        rawAmps  = Array.fill(numPartials, { |i|
            if(i < n) { allAmps[i] } { 0.0 }
        });
        ^[rawFreqs, rawAmps]
    }

    // Peak normalise: scale so maximum amplitude = 1.0
    // Subclasses may override for RMS normalise or other strategies.
    prNormalise { |rawAmps|
        var mx = rawAmps.maxItem.max(1e-12);
        ^(rawAmps / mx).collect(_.asFloat)
    }

}
