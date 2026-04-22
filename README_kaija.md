# Kaija

A polyphonic MIDI performance synthesiser for SuperCollider, named after Kaija Saariaho (1952–2023), Finnish composer.

Part of the **Kassia suite** — a family of SuperCollider instruments sharing a common spectral backend.

---

## Architecture

Kaija extends Kassia's base architecture for polyphonic MIDI performance:

```
KaijaSpectralModel  →  KaijaSynth  →  KaijaController  →  KaijaView
       ↑                    ↑
KassiaSpectralModel    KassiaSynth   (base classes, shared with Kassia)
```

- **KaijaSpectralModel** — extends KassiaSpectralModel; adds RMS normalisation for consistent loudness across timbral changes
- **KaijaSynth** — extends KassiaSynth; adds ADSR envelope, velocity, pitch bend, per-voice node management
- **KaijaScale** — Scala (.scl) tuning file loader with nearest-degree mapping for untuned notes
- **KaijaController** — 8-voice pool with round-robin allocation, MIDI responders, listener/notification system
- **KaijaView** — Qt GUI with 9 channel strips (8 partials + pink noise)

---

## Features

- 8-voice polyphony with round-robin voice allocation
- 8 additive FM partials with independent level, AM rate and depth per strip
- Pink noise source mixed in parallel with partials, through the same filter chain
- Spectral tilt (power law amplitude weighting across partials)
- ADSR amplitude envelope with filter envelope modulation amount
- MoogFF resonant low-pass filter with LFO modulation (rate + depth)
- Pitch bend (configurable range, default ±2 semitones)
- Mod wheel (CC1) → vowel position
- Timbre CC (default CC74) → FM index modulation with configurable range
- Sustain pedal (CC64)
- Vowel formant processing (Peterson & Barney vowel space)
- Drive
- Ratio and index smoothing (lag) for glide-like timbral transitions
- Scala tuning file support (.scl) with 12TET default
- MIDI flush (hard-kills all voices immediately)
- Randomise phases

---

## Installation

1. Install Kassia's extensions first — Kaija depends on `KassiaSpectralModel`, `KassiaSynth`, and `KassiaFormant`. See [github.com/matyas47/kassia](https://github.com/matyas47/kassia).
2. Copy the contents of `extensions/` to your SuperCollider Extensions directory:
   ```
   /usr/local/share/SuperCollider/Extensions/
   ```
3. Recompile the class library (`Ctrl+Shift+L` in the IDE)
4. Open `Kaija.scd` and evaluate the whole file (`Ctrl+Return`)

---

## Dependencies

- SuperCollider 3.12+
- Kassia extensions (`KassiaSpectralModel`, `KassiaSynth`, `KassiaFormant`)
- `FMRatioPartials` — must be installed separately
- `PitchView` — must be installed separately
- `FormantVowel` — must be installed separately

---

## MIDI

Kaija responds to standard MIDI messages:

| Message | Function |
|---|---|
| Note On/Off | Voice allocation and release |
| Pitch Bend | Pitch bend (configurable range in UI) |
| CC1 | Mod wheel → vowel position |
| CC64 | Sustain pedal |
| CC74 (default) | Timbre → FM index modulation |

MIDI channel defaults to omni. Channel, timbre CC number, timbre range, and pitch bend range are all configurable in the UI.

---

## File Structure

```
Kaija.scd                     launcher script
extensions/
  KaijaSpectralModel.sc       FM partial model with RMS normalisation
  KaijaSynth.sc               per-voice render engine with ADSR and pitch bend
  KaijaScale.sc               Scala tuning file loader
  KaijaController.sc          voice pool, MIDI responders, state management
  KaijaView.sc                Qt GUI
```

---

## The Kassia Suite

Kaija is part of a family of SuperCollider instruments named after composers:

| Instrument | Description | Status |
|---|---|---|
| [Kassia](https://github.com/matyas47/kassia) | Drone synthesiser | v0.2 |
| Kaija | Polyphonic MIDI FM synth | v0.1 |
| Hildegard | Resonator processor | complete |
| Eliane | Percussive/sampling instrument | v0.1 |
| Daphne | Granular synthesiser | in progress |
