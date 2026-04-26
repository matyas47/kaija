// Kassia.scd
// Launcher for Kassia v0.5
//
// Boots the server if needed, then creates the model, synth,
// controller, and view in the correct order.
//
// Class files must be installed in your SuperCollider Extensions directory:
//   ~/.local/share/SuperCollider/Extensions/
// or equivalent, and SuperCollider recompiled after installation.
//
// Required class files:
//   KassiaSpectralModel.sc
//   KassiaSynth.sc
//   KassiaController.sc
//   KassiaView.sc
//   KassiaFormant.sc
//   FMRatioPartials.sc  (must be installed separately if not already present)
//   PitchView.sc

(

s.waitForBoot {

	var model, synth, ctrl, view, win;

	// --- Spectral model ---
	model = KassiaSpectralModel(
		carrier:     55,
		ratio:       1.0,
		index:       3.0,
		numPartials: 8
	);

	// --- Render engine ---
	KassiaSynth.addDef(8, \kassia_core);
	s.sync;

	synth = KassiaSynth(num: 8, partialGain: 0.14);

	// --- Controller ---
	ctrl = KassiaController(model: model, synth: synth);

	// --- Window ---
	win = Window("Kassia", Rect(20, 20, 1274, 615)).front;

	win.onClose_({
		view.free;
		ctrl.free;
	});

	// --- View ---
	view = KassiaView(controller: ctrl, window: win);

	// --- Start audio ---
	ctrl.play(s, root: 55, master: 0.12);

};

)
