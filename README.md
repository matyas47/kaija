# Kassia

**Kassia** is a SuperCollider-based drone instrument built around FM-derived partials, additive synthesis, and formant filtering. It generates evolving harmonic structures using a bank of oscillators whose frequencies and amplitudes are derived from FM partial analysis. These partials can be shaped with amplitude, panning, modulation, and filtering controls, producing slowly evolving drones, installation soundscapes, and spectral textures.

Kassia is named after **Kassia (Kassiani) of Byzantium**, a 9th-century composer and poet.

---

## Features

- 8-voice additive drone engine
- FM-derived partial generation with real-time ratio morphing
- Per-partial amplitude, panning, FM depth, AM rate/depth, and FM rate controls
- Per-formant bandwidth vowel/formant filtering stage (FormantVowel)
- Moog-style master filter with LFO modulation
- Real-time spectrum display
- Smooth parameter interpolation throughout (server-side lag)
- Clean four-layer architecture: spectral model, render engine, controller, view

---

## Architecture

Kassia v0.2 is structured in four layers:

**KassiaSpectralModel** — pure spectral logic with no server or GUI dependency. Computes FM-derived partial frequencies and normalised amplitudes from carrier, ratio, and index parameters.

**KassiaSynth** — the render engine. Owns the SC server node and SynthDef. Handles additive synthesis, per-partial modulation, formant filtering, and the Moog VCF. Designed as a base class for future instruments (Kaija, Eliane).

**KassiaController** — the glue layer. Owns the spectral model, synth, morph routine, and a push-based listener/callback system. The view calls controller methods; the controller notifies the view of state changes.

**KassiaView** — the UI layer. Registers listeners on the controller at startup and rebuilds widgets from current window bounds. Contains reusable `prMakePartialStrip` and `prMakeSliderNb` helpers.

---

## Requirements

- SuperCollider 3.13+
- Tested on Linux (Qt GUI)
- `FMRatioPartials.sc` and `PitchView.sc` must be installed in your Extensions directory

No external plugins are required.

---

## Files

```
Kassia.scd              # launcher — run this to start the instrument
KassiaSpectralModel.sc  # spectral logic (Extensions)
KassiaSynth.sc          # render engine / SynthDef (Extensions)
KassiaController.sc     # controller / state / callbacks (Extensions)
KassiaView.sc           # UI layer (Extensions)
KassiaFormant.sc        # per-formant bandwidth vowel filter (Extensions)
```

---

## Installation

Clone the repository:

```bash
git clone https://github.com/matyas47/kassia.git
```

Copy the class files to your SuperCollider Extensions directory:

```bash
cp KassiaSpectralModel.sc KassiaSynth.sc KassiaController.sc \
   KassiaView.sc KassiaFormant.sc \
   /usr/local/share/SuperCollider/Extensions/
```

Recompile the class library in SuperCollider (`Language → Recompile Class Library` or `Ctrl+Shift+L`).

---

## Running Kassia

Make sure the SuperCollider server is running (`s.boot`), then open and run `Kassia.scd`. The instrument will boot, register the SynthDef, and open the GUI window automatically.

---

## Controls

**Row 1 — carrier and global**
`carrier/root Hz` sets the fundamental frequency. `master` controls overall output level. `vcf` and `Q` control the Moog-style filter cutoff and resonance.

**Row 2 — spectral model**
`mod ratio` and `index` define the FM partial structure. `mod Hz` shows the resulting modulator frequency as a read-only display. `target`, `time`, `Morph`, and `Stop morph` control ratio morphing — the ratio interpolates exponentially from its current value to the target over the specified duration in seconds.

**Row 3 — timbre**
`drive` controls pre-filter saturation. `vMix`, `vPos`, and `vRQ` control the vowel/formant filter mix, vowel position (A→E→I→O→U), and bandwidth multiplier respectively. `fModHz` and `fModDp` control LFO modulation of the VCF cutoff.

**Channel strips**
Each strip controls one partial. The vertical slider and number box set level (0–1 normalised). `pan` sets stereo position. `fmCt` sets per-partial FM depth in cents. `amHz` and `amDp` set AM rate and depth. `fmHz` sets per-partial FM rate.

**Rand phases** randomises the initial phase of all oscillators for timbral variation. **Init levels** resets partial amplitudes to the FM-derived values for the current ratio and index.

---

## Use Cases

Kassia was designed for long-form drone composition, ambient and electroacoustic textures, installation sound environments, and spectral exploration of FM partial structures.

---

## License

GPL v3. This license does not apply to audio generated using this software.
