// Kaija.scd
// Launcher for Kaija v0.5
//
// Boots the server if needed, then creates the model, voice pool,
// scale, controller, and view in the correct order.
// MIDI is initialised by the controller on startup.
//
// Class files must be installed in your SuperCollider Extensions directory:
//   /usr/local/share/SuperCollider/Extensions/dgm_extensions/
// and SuperCollider recompiled after installation.
//
// Required class files:
//   KassiaSpectralModel.sc  (shared with Kassia)
//   KassiaFormant.sc        (shared with Kassia)
//   KassiaSynth.sc          (base class, shared with Kassia)
//   KaijaSpectralModel.sc
//   KaijaSynth.sc
//   KaijaScale.sc
//   KaijaController.sc
//   KaijaView.sc
//   FMRatioPartials.sc      (must be installed separately if not already)
//   PitchView.sc

(

s.waitForBoot {

	var model, voices, scale, ctrl, view, win;
	var numVoices = 8;
	var numPartials = 8;

	// --- Spectral model ---
	model = KaijaSpectralModel(
		carrier:     261.626,   // middle C
		ratio:       1.0,
		index:       3.0,
		numPartials: numPartials,
		tilt:        0.0
	);

	// --- Voice pool ---
	// SynthDef registered once; each voice is a separate instance
	KaijaSynth.addDef(numPartials, \kaija_core);
	s.sync;

	voices = Array.fill(numVoices, {
		KaijaSynth(num: numPartials)
	});

	// --- Scale ---
	scale = KaijaScale(rootMidi: 60, rootHz: 261.626);

	// --- Controller ---
	ctrl = KaijaController(
		model:  model,
		voices: voices,
		scale:  scale,
		server: s
	);

	// --- Window ---
	// Window width: 9 strips × 81px + 4px margin
	win = Window("Kaija", Rect(20, 20, (9 * 81) + 4, 525)).front;

	win.onClose_({
		view.free;
		ctrl.free;
	});

	// --- View ---
	view = KaijaView(controller: ctrl, window: win);

	// --- Start audio and MIDI ---
	ctrl.play(s);
	ctrl.startMIDI;

};

)
