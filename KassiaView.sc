// KassiaView.sc
// View layer for Kassia.
// Accepts a KassiaController and a Window.
// Registers listeners on the controller at init.
// Builds and rebuilds UI via buildUI (called on startup and by "Fit window").
//
// No business logic lives here — all value conversion and state
// mutation goes through the controller.
//
// Requires: KassiaController.sc, PitchView.sc

KassiaView {

	var ctrl;         // KassiaController
	var win;          // Window

	var top, mid;     // CompositeViews
	var presetRow;    // CompositeView for preset controls
	var scope;        // FreqScopeView

	// Top bar widgets (retained for listener callbacks)
	var carrierNb, carrierPitchTxt;
	var modHzNb, modPitchTxt;
	var ratioNb, ratioSl;
	var indexNb, indexSl;

	// Preset widgets
	var presetMenu, presetNameField;

	// Per-partial strip widget arrays (rebuilt on each buildUI)
	var freqNb, freqPitchTxt;
	var levelSl, levelNb;

	var uiFont, dark, stripBg, txtCol, lvlCol, ctrlCol, fmCol;

	*new { |controller, window|
		^super.new.init(controller, window)
	}

	init { |c, w|
		ctrl = c;
		win  = w;

		uiFont = KassiaPlatform.uiFont(10);
		dark   = Color.grey(0.92);
		stripBg = Color.grey(0.82);
		txtCol  = Color.black;
		lvlCol  = Color.grey(0.93);
		ctrlCol = Color.grey(0.80);   // shared by pan/am/fm controls
		fmCol   = Color.grey(0.80);   // FM-specific controls (kept for future divergence)

		this.prRegisterListeners;
		this.buildUI;
	}

	// ------------------------------------------------------------------
	// Listener registration
	// Listeners are registered once and persist across buildUI calls.
	// Each callback updates only the relevant widget(s) if they exist.
	// ------------------------------------------------------------------

	prRegisterListeners {

		ctrl.addListener(\refresh, { |carrier, ratio, index, tilt, modHz, absFreqs, amps|
			{
				if(carrierNb.notNil)       { carrierNb.value = carrier };
				if(carrierPitchTxt.notNil) { carrierPitchTxt.string = PitchView.hzToPitchString(carrier, 0.1) };
				if(ratioNb.notNil)         { ratioNb.value = ratio.round(0.001) };
				if(ratioSl.notNil)         { ratioSl.value = ratio.explin(0.125, 8.0, 0, 1) };
				if(indexNb.notNil)         { indexNb.value = index.round(0.001) };
				if(indexSl.notNil)         { indexSl.value = index.linlin(0.0, 10.0, 0, 1) };
				if(modHzNb.notNil)         { modHzNb.value = modHz.round(0.001) };
				if(modPitchTxt.notNil)     { modPitchTxt.string = PitchView.hzToPitchString(modHz, 0.1) };
				ctrl.synth.num.do { |i|
					if(freqNb.notNil and: { freqNb[i].notNil }) {
						freqNb[i].value = absFreqs[i].round(0.001);
					};
					if(freqPitchTxt.notNil and: { freqPitchTxt[i].notNil }) {
						freqPitchTxt[i].string = PitchView.hzToPitchString(absFreqs[i], 0.1);
					};
					if(levelSl.notNil and: { levelSl[i].notNil }) {
						levelSl[i].value = amps[i];
					};
					if(levelNb.notNil and: { levelNb[i].notNil }) {
						levelNb[i].value = amps[i].round(0.001);
					};
				};
			}.defer;
		});

		ctrl.addListener(\carrier, { |hz|
			{ if(carrierNb.notNil)       { carrierNb.value = hz };
			  if(carrierPitchTxt.notNil) { carrierPitchTxt.string = PitchView.hzToPitchString(hz, 0.1) };
			}.defer;
		});

		ctrl.addListener(\ratio, { |r|
			{ if(ratioNb.notNil) { ratioNb.value = r.round(0.001) };
			  if(ratioSl.notNil) { ratioSl.value = r.explin(0.125, 8.0, 0, 1) };
			}.defer;
		});

		ctrl.addListener(\index, { |i|
			{ if(indexNb.notNil) { indexNb.value = i.round(0.001) };
			  if(indexSl.notNil) { indexSl.value = i.linlin(0.0, 10.0, 0, 1) };
			}.defer;
		});

		ctrl.addListener(\modHz, { |hz|
			{ if(modHzNb.notNil)    { modHzNb.value = hz.round(0.001) };
			  if(modPitchTxt.notNil) { modPitchTxt.string = PitchView.hzToPitchString(hz, 0.1) };
			}.defer;
		});

		ctrl.addListener(\partials, { |absFreqs, amps|
			{
				ctrl.synth.num.do { |i|
					if(freqNb.notNil and: { freqNb[i].notNil }) {
						freqNb[i].value = absFreqs[i].round(0.001);
					};
					if(freqPitchTxt.notNil and: { freqPitchTxt[i].notNil }) {
						freqPitchTxt[i].string = PitchView.hzToPitchString(absFreqs[i], 0.1);
					};
					if(levelSl.notNil and: { levelSl[i].notNil }) {
						levelSl[i].value = amps[i];
					};
					if(levelNb.notNil and: { levelNb[i].notNil }) {
						levelNb[i].value = amps[i].round(0.001);
					};
				};
			}.defer;
		});

		ctrl.addListener(\presets, { |names|
			{ if(presetMenu.notNil) {
				presetMenu.items_(["— presets —", "Load file..."] ++ names);
			}}.defer;
		});

		ctrl.addListener(\presetLoaded, { |name|
			{ if(presetMenu.notNil) {
				var idx = (["— presets —", "Load file..."] ++ ctrl.presetBank.names).indexOf(name) ?? { 0 };
				presetMenu.value_(idx);
			}}.defer;
		});
	}

	// ------------------------------------------------------------------
	// UI construction
	// Called on startup and by the "Fit window" button.
	// Tears down existing views and rebuilds from current window bounds.
	// ------------------------------------------------------------------

	buildUI {
		var winW, winH, startX, stripW;

		// Tear down existing views
		if(top.notNil)       { top.remove;       top = nil };
		if(presetRow.notNil) { presetRow.remove;  presetRow = nil };
		if(mid.notNil)       { mid.remove;        mid = nil };

		// Nil out widget refs so listener guards work correctly
		carrierNb = nil; carrierPitchTxt = nil;
		modHzNb = nil; modPitchTxt = nil;
		ratioNb = nil; ratioSl = nil;
		indexNb = nil; indexSl = nil;
		presetMenu = nil; presetNameField = nil;
		freqNb       = Array.newClear(ctrl.synth.num);
		freqPitchTxt = Array.newClear(ctrl.synth.num);
		levelSl      = Array.newClear(ctrl.synth.num);
		levelNb      = Array.newClear(ctrl.synth.num);

		winW = win.bounds.width;
		winH = win.bounds.height;

		win.background_(dark);

		top = CompositeView(win, Rect(0, 0, winW, 120));
		top.background_(dark);

		presetRow = CompositeView(win, Rect(0, 120, winW, 24));
		presetRow.background_(dark);

		mid = CompositeView(win, Rect(0, 144, winW, winH - 163));
		mid.background_(dark);

		this.prBuildTopBar(winW);
		this.prBuildPresetRow(winW);
		this.prBuildSpectrum(winW);

		startX = 8;
		stripW = ((winW - (startX * 2)) / ctrl.synth.num).floor.asInteger.min(168);
		this.prBuildStrips(startX, stripW);

		// Restore all readouts from current controller/synth state
		ctrl.refreshPartials;
	}

	// ------------------------------------------------------------------
	// Preset row
	// ------------------------------------------------------------------

	prBuildPresetRow { |winW|
		var btnFont;
		btnFont = KassiaPlatform.btnFont(8);

		presetMenu = PopUpMenu(presetRow, Rect(4, 3, 200, 18))
			.items_(["— presets —", "Load file..."])
			.font_(uiFont)
			.action_({ |m|
				if(m.value == 1) {
					Dialog.openPanel(
						okFunc: { |p| ctrl.readPresets(p) },
						cancelFunc: { presetMenu.value_(0) },
						path: KassiaPlatform.ensurePresetsDir("kassia")
					);
				} {
					if(m.value > 1) {
						var name = ctrl.presetBank.names[m.value - 2];
						ctrl.loadPreset(name);
					};
				};
			});

		presetNameField = TextField(presetRow, Rect(210, 3, 140, 18))
			.string_("preset name")
			.font_(uiFont);

		Button(presetRow, Rect(354, 3, 40, 18))
			.states_([["Save"]])
			.font_(btnFont)
			.action_({
				var name = presetNameField.string.stripWhiteSpace;
				if(name.size > 0 and: { name != "preset name" }) {
					ctrl.savePreset(name);
				} {
					presetNameField.string_("⚠ enter a name");
				};
			});

		Button(presetRow, Rect(398, 3, 46, 18))
			.states_([["Delete"]])
			.font_(btnFont)
			.action_({
				if(presetMenu.value > 1) {
					var name = ctrl.presetBank.names[presetMenu.value - 2];
					ctrl.deletePreset(name);
					presetMenu.value_(0);
				};
			});

		Button(presetRow, Rect(448, 3, 56, 18))
			.states_([["Save file"]])
			.font_(btnFont)
			.action_({
				var name, path;
				name = presetNameField.string.stripWhiteSpace;
				if(name.size > 0 and: { name != "preset name" }) {
					ctrl.savePreset(name);
				};
				if(ctrl.presetBank.size == 0) {
					presetNameField.string_("⚠ enter a preset name first");
				} {
					if(ctrl.bankPath.notNil) {
						if(ctrl.writePresets(ctrl.bankPath)) {
							presetNameField.string_("✓ saved");
						} {
							presetNameField.string_("⚠ save failed");
						};
					} {
						var saveName = if(name.size > 0 and: { name != "preset name" })
							{ name } { "kassia_presets" };
						path = KassiaPlatform.ensurePresetsDir("kassia") ++ "/" ++ saveName ++ ".json";
						if(ctrl.writePresets(path)) {
							presetNameField.string_("✓ saved: " ++ saveName);
						} {
							presetNameField.string_("⚠ save failed");
						};
					};
				};
			});
	}

	// ------------------------------------------------------------------
	// Top bar
	// ------------------------------------------------------------------

	prBuildTopBar { |winW|
		var ratioTargetNb, ratioTimeNb;

		// Row 1 — carrier, master, vcf, Q, buttons
		StaticText(top, Rect(14, 10, 110, 20))
			.string_("carrier/root Hz").stringColor_(txtCol).font_(uiFont);

		carrierNb = NumberBox(top, Rect(120, 8, 95, 22))
			.decimals_(3).step_(0.001)
			.value_(ctrl.model.carrier)
			.action_({ |nb|
				ctrl.stopMorph;
				ctrl.setCarrier(nb.value);
			});
		// Explicitly set value after creation to ensure display is correct
		carrierNb.value = ctrl.model.carrier;

		carrierPitchTxt = StaticText(top, Rect(120, 30, 132, 14))
			.string_(PitchView.hzToPitchString(ctrl.model.carrier, 0.1))
			.stringColor_(txtCol).font_(uiFont);

		StaticText(top, Rect(235, 10, 60, 20))
			.string_("master").stringColor_(txtCol).font_(uiFont);
		this.prMakeSlider(top, Rect(290, 12, 180, 16),
			val: 0.12 / 0.5,
			bg: lvlCol,
			action: { |v| ctrl.set(\master, v * 0.5) }
		);

		StaticText(top, Rect(490, 10, 60, 20))
			.string_("vcf").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(525, 12, 220, 16),
			nbRect: Rect(750, 8, 82, 22),
			initVal: 1200,
			bg: ctrlCol,
			toSlider: { |v| v.explin(20, 20000, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 20, 20000) },
			action: { |v| ctrl.set(\vcfFreq, v) },
			decimals: 0
		);

		StaticText(top, Rect(845, 10, 40, 20))
			.string_("Q").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(875, 12, 110, 16),
			nbRect: Rect(990, 8, 68, 22),
			initVal: 0.35,
			bg: ctrlCol,
			toSlider: { |v| v.linlin(0.05, 0.95, 0, 1) },
			fromSlider: { |v| v.linlin(0, 1, 0.05, 0.95) },
			action: { |v| ctrl.set(\vcfRQ, v) },
			decimals: 2
		);

		Button(top, Rect(1048, 8, 100, 22))
			.states_([["Rand phases"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({ ctrl.randomisePhases });

		Button(top, Rect(1152, 8, 100, 22))
			.states_([["Init levels"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({ ctrl.initLevels });

		// Row 2 — mod ratio, mod Hz, index, init levels, morph controls
		StaticText(top, Rect(14, 48, 70, 20))
			.string_("mod ratio").stringColor_(txtCol).font_(uiFont);

		ratioSl = Slider(top, Rect(80, 50, 220, 16))
			.value_(ctrl.model.ratio.explin(0.125, 8.0, 0, 1))
			.background_(fmCol)
			.action_({ |sl|
				ctrl.stopMorph;
				ctrl.setRatio(sl.value.linexp(0, 1, 0.125, 8.0));
			});

		ratioNb = NumberBox(top, Rect(305, 46, 82, 22))
			.decimals_(3).step_(0.0001)
			.value_(ctrl.model.ratio)
			.action_({ |nb|
				ctrl.stopMorph;
				ctrl.setRatio(nb.value);
			});

		StaticText(top, Rect(405, 48, 60, 20))
			.string_("mod Hz").stringColor_(txtCol).font_(uiFont);

		modHzNb = NumberBox(top, Rect(455, 46, 95, 22))
			.decimals_(3).enabled_(false)
			.value_(ctrl.model.modHz);

		modPitchTxt = StaticText(top, Rect(455, 68, 132, 14))
			.string_(PitchView.hzToPitchString(ctrl.model.modHz, 0.1))
			.stringColor_(txtCol).font_(uiFont);

		StaticText(top, Rect(570, 48, 70, 20))
			.string_("index").stringColor_(txtCol).font_(uiFont);

		indexSl = Slider(top, Rect(615, 50, 150, 16))
			.value_(ctrl.model.index.linlin(0.0, 10.0, 0, 1))
			.background_(fmCol)
			.action_({ |sl| ctrl.setIndex(sl.value.linlin(0, 1, 0.0, 10.0)) });

		indexNb = NumberBox(top, Rect(770, 46, 70, 22))
			.decimals_(3).step_(0.001)
			.value_(ctrl.model.index)
			.action_({ |nb| ctrl.setIndex(nb.value) });

		// morph controls
		StaticText(top, Rect(850, 48, 42, 20))
			.string_("target").stringColor_(txtCol).font_(uiFont);
		ratioTargetNb = NumberBox(top, Rect(893, 46, 60, 22))
			.decimals_(3).step_(0.0001).value_(1.0);

		StaticText(top, Rect(957, 48, 30, 20))
			.string_("time").stringColor_(txtCol).font_(uiFont);
		ratioTimeNb = NumberBox(top, Rect(987, 46, 56, 22))
			.decimals_(3).step_(0.001).value_(10.0);

		StaticText(top, Rect(1046, 48, 10, 20))
			.string_("s").stringColor_(txtCol).font_(uiFont);

		Button(top, Rect(1059, 46, 54, 22))
			.states_([["Morph"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({ ctrl.morphRatioTo(ratioTargetNb.value, ratioTimeNb.value) });

		Button(top, Rect(1118, 46, 80, 22))
			.states_([["Stop morph"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({ ctrl.stopMorph });

		// Row 3 — drive, vowels, filter modulation
		StaticText(top, Rect(14, 82, 50, 20))
			.string_("drive").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(60, 84, 140, 16),
			nbRect: Rect(205, 80, 70, 22),
			initVal: 1.0,
			bg: ctrlCol,
			toSlider: { |v| v.explin(0.25, 8.0, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 0.25, 8.0) },
			action: { |v| ctrl.set(\drive, v) },
			decimals: 2
		);

		StaticText(top, Rect(290, 82, 70, 20))
			.string_("vMix").stringColor_(txtCol).font_(uiFont);
		this.prMakeSlider(top, Rect(330, 84, 120, 16),
			val: 0.35.linexp(0.001, 1.0, 0, 1),
			bg: ctrlCol,
			action: { |v| ctrl.set(\vowelMix, v.linexp(0, 1, 0.001, 1.0)) }
		);

		StaticText(top, Rect(470, 82, 70, 20))
			.string_("vPos").stringColor_(txtCol).font_(uiFont);
		this.prMakeSlider(top, Rect(510, 84, 120, 16),
			val: 1.5 / 4,
			bg: ctrlCol,
			action: { |v| ctrl.set(\vowelPos, v * 4) }
		);

		// vRQ: bandwidth multiplier — 1.0 = natural, >1 = wider, <1 = tighter
		StaticText(top, Rect(650, 82, 70, 20))
			.string_("vRQ").stringColor_(txtCol).font_(uiFont);
		this.prMakeSlider(top, Rect(685, 84, 120, 16),
			val: 1.0.linlin(0.5, 3.0, 0, 1),
			bg: ctrlCol,
			action: { |v| ctrl.set(\vowelRQ, v.linlin(0, 1, 0.5, 3.0)) }
		);

		StaticText(top, Rect(825, 82, 54, 20))
			.string_("fModHz").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(875, 84, 100, 16),
			nbRect: Rect(978, 80, 58, 22),
			initVal: 0.03,
			bg: ctrlCol,
			toSlider: { |v| v.explin(0.0001, 2.0, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 0.0001, 2.0) },
			action: { |v| ctrl.set(\filterModRate, v) },
			decimals: 3
		);

		StaticText(top, Rect(1040, 82, 50, 20))
			.string_("fModDp").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(1090, 84, 80, 16),
			nbRect: Rect(1174, 80, 56, 22),
			initVal: 0.0,
			bg: ctrlCol,
			toSlider: { |v| v },
			fromSlider: { |v| v.clip(0, 0.99) },
			action: { |v| ctrl.set(\filterModDepth, v) },
			decimals: 2
		);
	}

	// ------------------------------------------------------------------
	// Spectrum display
	// ------------------------------------------------------------------

	prBuildSpectrum { |winW|
		if(scope.notNil) { scope.active_(false); scope = nil };
		StaticText(top, Rect(14, 116, 120, 20))
			.string_("spectrum").stringColor_(txtCol).font_(uiFont);
		scope = FreqScopeView(top, Rect(14, 136, winW - 28, 88));
		scope.active_(true);
		scope.background_(Color.black);
		scope.freqMode_(1);
		scope.inBus_(0);
		scope.dbRange_(90);
	}

	// ------------------------------------------------------------------
	// Channel strips
	// ------------------------------------------------------------------

	prBuildStrips { |startX, stripW|
		ctrl.synth.num.do { |i|
			var x0;
			x0 = startX + (i * stripW);
			this.prMakePartialStrip(i, x0, stripW);
		};
	}

	prMakePartialStrip { |i, x0, stripW|
		var strip;

		strip = CompositeView(mid, Rect(x0, 6, stripW - 8, 330));
		strip.background_(stripBg);

		StaticText(strip, Rect(8, 6, 22, 14))
			.string_("P" ++ (i + 1)).stringColor_(txtCol).font_(uiFont);

		freqNb[i] = NumberBox(strip, Rect(28, 4, 68, 20))
			.enabled_(false).decimals_(3).value_(0);

		freqPitchTxt[i] = StaticText(strip, Rect(98, 6, 62, 14))
			.stringColor_(txtCol).font_(uiFont);

		// Level — vertical slider + number box
		// Slider and numberbox both work in 0–1 normalised space.
		// partialGain scaling is applied inside KassiaSynth.setPartialParamAt.
		levelSl[i] = Slider(strip, Rect(10, 36, 18, 268))
			.value_(0.0).background_(lvlCol)
			.action_({ |sl|
				ctrl.setPartialParamAt(\levels, i, sl.value);
			});

		levelNb[i] = NumberBox(strip, Rect(34, 36, 64, 20))
			.decimals_(3).step_(0.001).value_(0.0)
			.action_({ |nb|
				var lv;
				lv = nb.value.clip(0.0, 1.0);
				levelSl[i].value = lv;
				ctrl.setPartialParamAt(\levels, i, lv);
			});

		StaticText(strip, Rect(34, 58, 60, 14))
			.string_("pan").stringColor_(txtCol).font_(uiFont);
		this.prMakeSlider(strip, Rect(34, 72, 118, 16),
			val: 0.5,
			bg: ctrlCol,
			action: { |v|
				ctrl.setPartialParamAt(\pans, i, v.linlin(0, 1, -1, 1));
			}
		);

		StaticText(strip, Rect(34, 96, 60, 14))
			.string_("fmCt").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(strip,
			slRect: Rect(34, 110, 58, 16),
			nbRect: Rect(96, 108, 60, 20),
			initVal: 0.0,
			bg: fmCol,
			toSlider: { |v| v.linlin(0, 60, 0, 1) },
			fromSlider: { |v| v.linlin(0, 1, 0, 60) },
			action: { |v| ctrl.setPartialParamAt(\fmDepthCents, i, v) },
			decimals: 1
		);

		StaticText(strip, Rect(34, 134, 60, 14))
			.string_("amHz").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(strip,
			slRect: Rect(34, 148, 58, 16),
			nbRect: Rect(96, 146, 60, 20),
			initVal: 0.05,
			bg: ctrlCol,
			toSlider: { |v| v.explin(0.0001, 2.0, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 0.0001, 2.0) },
			action: { |v| ctrl.setPartialParamAt(\amRate, i, v) },
			decimals: 3
		);

		StaticText(strip, Rect(34, 172, 60, 14))
			.string_("amDp").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(strip,
			slRect: Rect(34, 186, 58, 16),
			nbRect: Rect(96, 184, 60, 20),
			initVal: 0.2,
			bg: ctrlCol,
			toSlider: { |v| v },
			fromSlider: { |v| v.clip(0, 1) },
			action: { |v| ctrl.setPartialParamAt(\amDepth, i, v) },
			decimals: 2
		);

		StaticText(strip, Rect(34, 210, 60, 14))
			.string_("fmHz").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(strip,
			slRect: Rect(34, 224, 58, 16),
			nbRect: Rect(96, 222, 60, 20),
			initVal: 0.03,
			bg: fmCol,
			toSlider: { |v| v.explin(0.0001, 2.0, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 0.0001, 2.0) },
			action: { |v| ctrl.setPartialParamAt(\fmRate, i, v) },
			decimals: 3
		);
	}

	// ------------------------------------------------------------------
	// Widget helpers
	// ------------------------------------------------------------------

	free {
		if(scope.notNil) { scope.active_(false); scope = nil };
	}

	// Simple slider with no paired number box
	prMakeSlider { |parent, rect, val, bg, action|
		^Slider(parent, rect)
			.value_(val)
			.background_(bg)
			.action_({ |sl| action.(sl.value) });
	}

	// Linked slider + number box pair.
	// toSlider:   converts domain value -> slider 0–1 position
	// fromSlider: converts slider 0–1 position -> domain value
	// action:     called with the domain value whenever either widget changes
	// decimals:   numberbox decimals (default 3)
	prMakeSliderNb { |parent, slRect, nbRect, initVal, bg,
	                  toSlider, fromSlider, action, decimals=3|
		var sl, nb, step;

		step = (10 ** decimals.neg);

		sl = Slider(parent, slRect)
			.value_(toSlider.(initVal))
			.background_(bg);

		nb = NumberBox(parent, nbRect)
			.decimals_(decimals).step_(step)
			.value_(initVal);

		sl.action_({ |s|
			var v = fromSlider.(s.value);
			nb.value = v.round(step);
			action.(v);
		});

		nb.action_({ |n|
			var v = n.value;
			sl.value = toSlider.(v);
			action.(v);
		});

		^[sl, nb]
	}

}
