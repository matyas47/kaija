
FMRatioPartials {

    var <fc, <ratio, <index, <maxOrder;

    *new { |fc=110.0, ratio=1.0, index=3.0, maxOrder=200|
        ^super.newCopyArgs(fc, ratio, index, maxOrder)
    }

    fm { ^fc * ratio }

    // ---- PUBLIC API ----
    // Returns 8 strongest (by |amp|), then sorted by frequency (ascending).
    // Events: (order:, freq:, amp:)
    partials {
        var fmLocal, nMax, jTable, span, top, i, n, f, nn, a, e;

        fmLocal = this.fm;

        // Keep modest; recurrence is fine here, and top-8 doesn't need huge orders.
        nMax = (index.abs * 2 + 10).ceil.asInteger;
        nMax = nMax.min(maxOrder).max(8);

        jTable = this.besselTable(index.asFloat, nMax);

        top = Array.new;

        span = (2 * nMax) + 1;
        span.do { |ii|
            i = ii;
            n = i - nMax;

            f = (fc + (n * fmLocal)).asFloat;

            if(f > 0.0) {
                nn = n.abs.asInteger;
                a = jTable[nn];

                if(a.notNil and: { a.isNumber } and: { a.isNaN.not } and: { a.abs < 1e308 }) {

                    if(n < 0 and: { nn.odd }) { a = a.neg };

                    e = (order: n, freq: f, amp: a);

                    top = this.insertTop8(e, top);
                };
            };
        };

        top = top.sort({ |aEvent, bEvent| aEvent[\freq] < bEvent[\freq] });

        ^top
    }

    freqs { ^this.partials.collect({ |ev| ev[\freq] }) }
    amps  { ^this.partials.collect({ |ev| ev[\amp].abs }) }

    insertTop8 { |ev, arr|
        var out, i, inserted, aNew, aHere;

        out = arr.copy;
        inserted = false;
        aNew = ev[\amp].abs;

        out.size.do { |ii|
            i = ii;
            aHere = out[i][\amp].abs;
            if(inserted.not and: { aNew > aHere }) {
                out = out.insert(i, ev);
                inserted = true;
            };
        };

        if(inserted.not) {
            out = out.add(ev);
        };

        if(out.size > 8) { out = out.copyRange(0, 7) };

        ^out
    }

    besselJ0 { |x, terms=32|
        var sum, term, m, xx;

        sum = 1.0;
        term = 1.0;
        m = 1;
        xx = x * x;

        terms.do {
            term = term * (xx.neg / (4.0 * m * m));
            sum  = sum + term;
            m    = m + 1;
        };

        ^sum
    }

    besselJ1 { |x, terms=32|
        var sum, term, m, xx;

        sum = x * 0.5;
        term = sum;
        m = 1;
        xx = x * x;

        terms.do {
            term = term * (xx.neg / (4.0 * m * (m + 1)));
            sum  = sum + term;
            m    = m + 1;
        };

        ^sum
    }

    besselTable { |xIn, nMax|
        var j, x, ax, steps, n;

        x = xIn.asFloat;
        ax = x.abs;

        j = Array.newClear(nMax + 1);

        if(ax < 1e-12) {
            j[0] = 1.0;
            ^j
        };

        j[0] = this.besselJ0(x, 32);
        if(nMax >= 1) { j[1] = this.besselJ1(x, 32) };

        if(nMax >= 2) {
            steps = nMax - 1;
            steps.do { |k|
                n = k + 1;
                j[n + 1] = ((2.0 * n) / x) * j[n] - j[n - 1];
            };
        };

        ^j
    }
}
