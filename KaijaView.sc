// KaijaView.sc
// View layer for Kaija.
// Accepts a KaijaController and a Window.
// Registers listeners on the controller at init.
// Builds UI via buildUI.
//
// No business logic lives here — all value conversion and state
// mutation goes through the controller.
//
// Requires: KaijaController.sc, PitchView.sc

KaijaView {

	var ctrl;         // KaijaController
	var win;          // Window

	var top, mid;     // CompositeViews
	var presetRow;    // CompositeView for preset controls
	var voiceRow;     // CompositeView for voice activity indicators
	var voiceLights;  // Array of Views (voice activity indicators)

	// Top bar widgets retained for listener callbacks
	var ratioNb, ratioSl;
	var indexNb, indexSl;
	var tiltNb, tiltSl;
	var scaleNameTxt;
	var masterSl, masterNb;

	// Preset widgets
	var presetMenu, presetNameField;

	// Per-partial strip widget arrays
	var freqNb, freqPitchTxt;
	var levelSl, levelNb;

	var uiFont, smFont, dark, stripBg, txtCol, lvlCol, ctrlCol, fmCol;
	var activeCol, inactiveCol;

	*new { |controller, window|
		^super.new.init(controller, window)
	}

	init { |c, w|
		ctrl = c;
		win  = w;

		uiFont      = KassiaPlatform.uiFont(9);
		smFont      = KassiaPlatform.btnFont(7);
		dark        = Color.grey(0.92);
		stripBg     = Color.grey(0.82);
		txtCol      = Color.black;
		lvlCol      = Color.grey(0.93);
		ctrlCol     = Color.grey(0.80);   // shared by all non-level controls
		fmCol       = Color.grey(0.80);   // FM-specific (ratio/index/tilt)
		activeCol   = Color(0.3, 0.7, 0.4);
		inactiveCol = Color.grey(0.65);

		this.prRegisterListeners;
		this.buildUI;
	}

	// ------------------------------------------------------------------
	// Listener registration — all deferred for MIDI thread safety
	// ------------------------------------------------------------------

	prRegisterListeners {

		ctrl.addListener(\refresh, { |carrier, ratio, index, tilt, modHz, absFreqs, amps|
			{
				if(ratioNb.notNil) { ratioNb.value = ratio.round(0.001) };
				if(ratioSl.notNil) { ratioSl.value = ratio.explin(0.125, 8.0, 0, 1) };
				if(indexNb.notNil) { indexNb.value = index.round(0.001) };
				if(indexSl.notNil) { indexSl.value = index.linlin(0.0, 10.0, 0, 1) };
				if(tiltNb.notNil)  { tiltNb.value  = tilt.round(0.001) };
				if(tiltSl.notNil)  { tiltSl.value  = tilt.linlin(-1.0, 1.0, 0, 1) };
				ctrl.voices[0].num.do { |i|
					if(freqNb.notNil and: { freqNb[i].notNil }) {
						freqNb[i].value = absFreqs[i].round(0.01);
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

		ctrl.addListener(\tilt, { |t|
			{ if(tiltNb.notNil) { tiltNb.value = t.round(0.001) };
			  if(tiltSl.notNil) { tiltSl.value = t.linlin(-1.0, 1.0, 0, 1) };
			}.defer;
		});

		ctrl.addListener(\partials, { |absFreqs, amps|
			{
				ctrl.voices[0].num.do { |i|
					if(freqNb.notNil and: { freqNb[i].notNil }) {
						freqNb[i].value = absFreqs[i].round(0.01);
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

		ctrl.addListener(\voiceActivity, { |activity|
			{
				if(voiceLights.notNil) {
					activity.do({ |active, i|
						if(voiceLights[i].notNil) {
							voiceLights[i].background_(
								if(active) { activeCol } { inactiveCol }
							);
						};
					});
				};
			}.defer;
		});

		ctrl.addListener(\master, { |v|
			{ if(masterSl.notNil) { masterSl.value = v };
			  if(masterNb.notNil) { masterNb.value = v.round(0.001) };
			}.defer;
		});

		ctrl.addListener(\scale, { |name|
			{ if(scaleNameTxt.notNil) { scaleNameTxt.string = name } }.defer;
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
	// ------------------------------------------------------------------

	buildUI {
		var winW, winH, startX, stripW, uiW;

		if(top.notNil)       { top.remove;       top = nil };
		if(presetRow.notNil) { presetRow.remove;  presetRow = nil };
		if(voiceRow.notNil)  { voiceRow.remove;   voiceRow = nil };
		if(mid.notNil)       { mid.remove;        mid = nil };

		ratioNb = nil; ratioSl = nil;
		indexNb = nil; indexSl = nil;
		tiltNb  = nil; tiltSl  = nil;
		scaleNameTxt  = nil;
		masterSl      = nil; masterNb     = nil;
		presetMenu    = nil; presetNameField = nil;
		voiceLights   = Array.newClear(ctrl.voices.size);
		freqNb        = Array.newClear(ctrl.voices[0].num);
		freqPitchTxt  = Array.newClear(ctrl.voices[0].num);
		levelSl       = Array.newClear(ctrl.voices[0].num + 1);
		levelNb       = Array.newClear(ctrl.voices[0].num + 1);

		winW = win.bounds.width;
		winH = win.bounds.height;

		startX = 4;
		stripW = 81;
		uiW    = startX + (9 * stripW);

		win.background_(dark);

		top = CompositeView(win, Rect(0, 0, uiW, 140));
		top.background_(dark);

		presetRow = CompositeView(win, Rect(0, 140, uiW, 24));
		presetRow.background_(dark);

		voiceRow = CompositeView(win, Rect(0, 164, uiW, 24));
		voiceRow.background_(dark);

		mid = CompositeView(win, Rect(0, 188, uiW, 310));
		mid.background_(dark);

		this.prBuildTopBar(uiW);
		this.prBuildPresetRow(uiW);
		this.prBuildVoiceRow(uiW);
		this.prBuildStrips(startX, stripW);
		this.prMakeNoiseStrip(startX + (ctrl.voices[0].num * stripW), stripW);

		ctrl.refreshPartials;
	}

	// ------------------------------------------------------------------
	// Top bar — all rows scale to uiW
	// Layout uses a simple segment grid: uiW / N columns
	// Numberboxes are 44px wide, labels are small
	// ------------------------------------------------------------------

	prBuildTopBar { |uiW|
		var nb, sl, lbl;
		var c0, c1, c2, c3, c4;
		var seg4, seg5, seg6;
		var x, labelW, key;
		var btnFont;

		btnFont = KassiaPlatform.btnFont(7);

		nb   = 44;   // numberbox width
		sl   = 0;    // calculated per row
		lbl  = 28;   // label width

		// Row 1: mod ratio | index | tilt | [Rand phases button]
		// 3 param groups + button, using 4 columns
		seg4 = (uiW / 4).floor.asInteger;
		c0 = 0; c1 = seg4; c2 = seg4 * 2; c3 = seg4 * 3;

		// mod ratio
		StaticText(top, Rect(c0 + 4, 6, 56, 16))
			.string_("mod ratio").stringColor_(txtCol).font_(KassiaPlatform.uiFont(8));
		ratioSl = Slider(top, Rect(c0 + 62, 8, seg4 - nb - 68, 12))
			.value_(ctrl.model.ratio.explin(0.125, 8.0, 0, 1))
			.background_(fmCol)
			.action_({ |s| ctrl.setRatio(s.value.linexp(0, 1, 0.125, 8.0)) });
		ratioNb = NumberBox(top, Rect(c1 - nb - 2, 4, nb, 18))
			.decimals_(2).step_(0.001).font_(uiFont)
			.value_(ctrl.model.ratio)
			.action_({ |n| ctrl.setRatio(n.value) });

		// index
		StaticText(top, Rect(c1 + 4, 6, lbl, 16))
			.string_("index").stringColor_(txtCol).font_(uiFont);
		indexSl = Slider(top, Rect(c1 + 36, 8, seg4 - nb - 42, 12))
			.value_(ctrl.model.index.linlin(0.0, 10.0, 0, 1))
			.background_(fmCol)
			.action_({ |s| ctrl.setIndex(s.value.linlin(0, 1, 0.0, 10.0)) });
		indexNb = NumberBox(top, Rect(c2 - nb - 2, 4, nb, 18))
			.decimals_(2).step_(0.01).font_(uiFont)
			.value_(ctrl.model.index)
			.action_({ |n| ctrl.setIndex(n.value) });

		// tilt
		StaticText(top, Rect(c2 + 4, 6, lbl, 16))
			.string_("tilt").stringColor_(txtCol).font_(uiFont);
		tiltSl = Slider(top, Rect(c2 + 28, 8, seg4 - nb - 34, 12))
			.value_(ctrl.model.tilt.linlin(-1.0, 1.0, 0, 1))
			.background_(fmCol)
			.action_({ |s| ctrl.setTilt(s.value.linlin(0, 1, -1.0, 1.0)) });
		tiltNb = NumberBox(top, Rect(c3 - nb - 2, 4, nb, 18))
			.decimals_(2).step_(0.01).font_(uiFont)
			.value_(ctrl.model.tilt)
			.action_({ |n| ctrl.setTilt(n.value) });

		// Rand phases — small button, right-aligned on row 1
		Button(top, Rect(c3 + 2, 4, uiW - c3 - 6, 18))
			.states_([["Rand phases"]])
			.font_(btnFont)
			.action_({ ctrl.randomisePhases });

		// Row 2: att | dec | sus | rel | env→vcf
		seg5 = (uiW / 5).floor.asInteger;
		[
			["att",     0.01, { |v| v.explin(0.001,10,0,1) }, { |v| v.linexp(0,1,0.001,10) }, \attack,    3],
			["dec",     0.3,  { |v| v.explin(0.001,10,0,1) }, { |v| v.linexp(0,1,0.001,10) }, \decay,     2],
			["sus",     0.7,  { |v| v },                       { |v| v.clip(0,1) },             \sustain,   2],
			["rel",     0.5,  { |v| v.explin(0.001,10,0,1) }, { |v| v.linexp(0,1,0.001,10) }, \release,   2],
			["env→vcf", 0.5,  { |v| v },                       { |v| v.clip(0,1) },             \vcfEnvAmt, 2]
		].do({ |spec, i|
			var capturedKey;
			x           = seg5 * i;
			labelW      = if(i == 4) { 46 } { 22 };
			capturedKey = spec[4];
			StaticText(top, Rect(x + 4, 38, labelW, 14))
				.string_(spec[0]).stringColor_(txtCol).font_(uiFont);
			this.prMakeSliderNb(top,
				slRect: Rect(x + labelW + 6, 40, seg5 - nb - labelW - 12, 12),
				nbRect: Rect(x + seg5 - nb - 2, 36, nb, 18),
				initVal: spec[1],
				bg: ctrlCol,
				toSlider: spec[2],
				fromSlider: spec[3],
				action: { |v| ctrl.set(capturedKey, v) },
				decimals: spec[5]
			);
		});

		// Row 3: vcf | Q | drv | vPos  (vMix hardcoded 1.0, vRQ hardcoded 1.0)
		seg6 = (uiW / 4).floor.asInteger;
		[
			["vcf",  2000,  { |v| v.explin(20,20000,0,1) },  { |v| v.linexp(0,1,20,20000) },  \vcfFreq,  ctrlCol, 0],
			["Q",    0.35,  { |v| v.linlin(0.05,0.95,0,1) }, { |v| v.linlin(0,1,0.05,0.95) }, \vcfRQ,    ctrlCol, 2],
			["drv",  1.0,   { |v| v.explin(0.25,8,0,1) },   { |v| v.linexp(0,1,0.25,8) },    \drive,    ctrlCol, 2],
			["vPos", 0.375, { |v| v },                       { |v| v },                        \vowelPos4, ctrlCol, 2]
		].do({ |spec, i|
			var capturedKey;
			x           = seg6 * i;
			labelW      = 22;
			key         = spec[4];
			capturedKey = key;
			StaticText(top, Rect(x + 4, 72, labelW, 14))
				.string_(spec[0]).stringColor_(txtCol).font_(uiFont);
			this.prMakeSliderNb(top,
				slRect: Rect(x + labelW + 6, 74, seg6 - nb - labelW - 12, 12),
				nbRect: Rect(x + seg6 - nb - 2, 70, nb, 18),
				initVal: spec[1],
				bg: spec[5],
				toSlider: spec[2],
				fromSlider: spec[3],
				action: { |v|
					if(capturedKey == \vowelPos4)
						{ ctrl.set(\vowelPos, v * 4) }
						{ ctrl.set(capturedKey, v) };
				},
				decimals: spec[6]
			);
		});

		// Row 4 — MIDI | pb | LFO | flush  (scale moved to voice row)
		StaticText(top, Rect(4, 106, 46, 14))
			.string_("MIDI chan").stringColor_(txtCol).font_(uiFont);
		PopUpMenu(top, Rect(50, 104, 52, 18))
			.items_(["omni"] ++ (1..16).collect(_.asString))
			.font_(uiFont)
			.action_({ |m|
				ctrl.setMidiChannel(if(m.value == 0) { nil } { m.value - 1 });
			});

		StaticText(top, Rect(106, 106, 18, 14))
			.string_("CC").stringColor_(txtCol).font_(uiFont);
		NumberBox(top, Rect(124, 104, 30, 18))
			.decimals_(0).step_(1).font_(uiFont)
			.value_(ctrl.timbreCCNum)
			.action_({ |n| ctrl.setTimbreCCNum(n.value.asInteger) });

		StaticText(top, Rect(158, 106, 42, 14))
			.string_("CC rng").stringColor_(txtCol).font_(uiFont);
		NumberBox(top, Rect(200, 104, 30, 18))
			.decimals_(1).step_(0.1).font_(uiFont)
			.value_(ctrl.timbreRange)
			.action_({ |n| ctrl.setTimbreRange(n.value) });

		StaticText(top, Rect(234, 106, 16, 14))
			.string_("pb").stringColor_(txtCol).font_(uiFont);
		NumberBox(top, Rect(250, 104, 30, 18))
			.decimals_(1).step_(0.5).font_(uiFont)
			.value_(ctrl.pitchBendRange)
			.action_({ |n| ctrl.setPitchBendRange(n.value) });

		StaticText(top, Rect(286, 106, 40, 14))
			.string_("LFO hz").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(326, 108, 90, 10),
			nbRect: Rect(418, 104, 36, 18),
			initVal: 0.5,
			bg: ctrlCol,
			toSlider: { |v| v.explin(0.001, 10.0, 0, 1) },
			fromSlider: { |v| v.linexp(0, 1, 0.001, 10.0) },
			action: { |v| ctrl.set(\vcfLFORate, v) },
			decimals: 2
		);

		StaticText(top, Rect(460, 106, 40, 14))
			.string_("LFO dep").stringColor_(txtCol).font_(uiFont);
		this.prMakeSliderNb(top,
			slRect: Rect(500, 108, 90, 10),
			nbRect: Rect(592, 104, 36, 18),
			initVal: 0.0,
			bg: ctrlCol,
			toSlider: { |v| v },
			fromSlider: { |v| v.clip(0, 1) },
			action: { |v| ctrl.set(\vcfLFODepth, v) },
			decimals: 2
		);

		Button(top, Rect(634, 104, 60, 18))
			.states_([["MIDI flush"]])
			.font_(btnFont)
			.action_({ ctrl.flushMIDI });
	}

	// ------------------------------------------------------------------
	// Preset row
	// ------------------------------------------------------------------

	prBuildPresetRow { |uiW|
		var btnFont;
		btnFont = KassiaPlatform.btnFont(7);

		// Dropdown: "— presets —", "Load file...", then preset names
		// Selecting a preset loads it; selecting "Load file..." opens dialog
		presetMenu = PopUpMenu(presetRow, Rect(4, 3, 200, 18))
			.items_(["— presets —", "Load file..."])
			.font_(uiFont)
			.action_({ |m|
				if(m.value == 1) {
					// Load file...
					Dialog.openPanel(
						okFunc: { |p|
							("Loading presets from: " ++ p).postln;
							ctrl.readPresets(p);
						},
						cancelFunc: { presetMenu.value_(0) },
						path: KassiaPlatform.ensurePresetsDir("kaija")
					);
				} {
					if(m.value > 1) {
						var name = ctrl.presetBank.names[m.value - 2];
						ctrl.loadPreset(name);
					};
				};
			});

		presetNameField = TextField(presetRow, Rect(210, 3, 130, 18))
			.string_("preset name")
			.font_(uiFont);

		// Save: saves to memory, auto-saves to disk if a path is known
		Button(presetRow, Rect(344, 3, 38, 18))
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

		Button(presetRow, Rect(386, 3, 44, 18))
			.states_([["Delete"]])
			.font_(btnFont)
			.action_({
				if(presetMenu.value > 1) {
					var name = ctrl.presetBank.names[presetMenu.value - 2];
					ctrl.deletePreset(name);
					presetMenu.value_(0);
				};
			});

		Button(presetRow, Rect(434, 3, 54, 18))
			.states_([["Save file"]])
			.font_(btnFont)
			.action_({
				var name, path;
				name = presetNameField.string.stripWhiteSpace;

				// Auto-save current name to memory if valid
				if(name.size > 0 and: { name != "preset name" }) {
					ctrl.savePreset(name);
				};

				if(ctrl.presetBank.size == 0) {
					presetNameField.string_("⚠ enter a preset name first");
				} {
					// If bank path already set, save directly
					if(ctrl.bankPath.notNil) {
						if(ctrl.writePresets(ctrl.bankPath)) {
							presetNameField.string_("✓ saved");
						} {
							presetNameField.string_("⚠ save failed");
						};
					} {
						// First time — use preset name as filename, save to default dir
						var saveName = if(name.size > 0 and: { name != "preset name" })
							{ name } { "kaija_presets" };
						path = KassiaPlatform.ensurePresetsDir("kaija") ++ "/" ++ saveName ++ ".json";
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
	// Voice activity row — voices on left, scale controls on right
	// ------------------------------------------------------------------

	prBuildVoiceRow { |uiW|
		var numVoices, lightW, gap, startX, scaleX;

		numVoices = ctrl.voices.size;
		lightW    = 16;
		gap       = 4;
		startX    = 4;

		StaticText(voiceRow, Rect(startX, 5, 40, 14))
			.string_("voices:").stringColor_(txtCol).font_(uiFont);

		numVoices.do { |i|
			voiceLights[i] = View(voiceRow,
				Rect(startX + 44 + (i * (lightW + gap)), 5, lightW, 12))
				.background_(inactiveCol);
		};

		// Scale controls on the right side of the voice row
		scaleX = startX + 44 + (numVoices * (lightW + gap)) + 16;

		StaticText(voiceRow, Rect(scaleX, 5, 28, 14))
			.string_("scale").stringColor_(txtCol).font_(uiFont);
		scaleNameTxt = StaticText(voiceRow, Rect(scaleX + 32, 5, 50, 14))
			.string_(ctrl.scale.name).stringColor_(txtCol).font_(uiFont);
		Button(voiceRow, Rect(scaleX + 86, 3, 50, 18))
			.states_([["Load scale"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({
				Dialog.openPanel(
					okFunc: { |p| ctrl.loadScale(p) },
					cancelFunc: {}
				);
			});
		Button(voiceRow, Rect(scaleX + 140, 3, 50, 18))
			.states_([["Clear scale"]])
			.font_(KassiaPlatform.btnFont(7))
			.action_({ ctrl.clearScale });

		// Master level — right of scale controls
		StaticText(voiceRow, Rect(scaleX + 198, 5, 36, 14))
			.string_("master").stringColor_(txtCol).font_(uiFont);
		masterSl = Slider(voiceRow, Rect(scaleX + 236, 6, 120, 12))
			.value_(0.5)
			.background_(lvlCol)
			.action_({ |s| ctrl.set(\master, s.value) });
		masterNb = NumberBox(voiceRow, Rect(scaleX + 360, 3, 44, 18))
			.decimals_(2).step_(0.01).font_(uiFont)
			.value_(0.5)
			.action_({ |n|
				var v = n.value.clip(0, 1);
				masterSl.value = v;
				ctrl.set(\master, v);
			});
	}

	// ------------------------------------------------------------------
	// Channel strips — sized to fit within stripW
	// ------------------------------------------------------------------

	prBuildStrips { |startX, stripW|
		ctrl.voices[0].num.do { |i|
			this.prMakePartialStrip(i, startX + (i * stripW), stripW);
		};
	}

	prMakePartialStrip { |i, x0, stripW|
		var strip, sw, nb, sl;

		sw    = stripW - 6;   // usable strip width
		nb    = sw - 4;       // numberbox width fits strip
		sl    = sw - 4;       // slider width fits strip

		strip = CompositeView(mid, Rect(x0, 4, sw, 300));
		strip.background_(stripBg);

		// Freq readout — number box only, pitch below
		freqNb[i] = NumberBox(strip, Rect(2, 2, sw - 2, 16))
			.enabled_(false).decimals_(1).value_(0).font_(uiFont);

		freqPitchTxt[i] = StaticText(strip, Rect(2, 18, sw - 2, 14))
			.stringColor_(txtCol).font_(uiFont);

		// Level vertical slider + numberbox
		levelSl[i] = Slider(strip, Rect(2, 34, 14, 162))
			.value_(0.0).background_(lvlCol)
			.action_({ |s| ctrl.setPartialParamAt(\levels, i, s.value) });

		levelNb[i] = NumberBox(strip, Rect(2, 198, sw - 2, 16))
			.decimals_(3).step_(0.001).value_(0.0).font_(uiFont)
			.action_({ |n|
				var lv = n.value.clip(0, 1);
				levelSl[i].value = lv;
				ctrl.setPartialParamAt(\levels, i, lv);
			});

		// Per-partial attack
		StaticText(strip, Rect(2, 216, sl, 12))
			.string_("atk").stringColor_(txtCol).font_(KassiaPlatform.uiFont(8));
		this.prMakeSliderNb(strip,
			slRect: Rect(2, 228, sl, 10),
			nbRect: Rect(2, 240, sl, 14),
			initVal: 0.0,
			bg: ctrlCol,
			toSlider: { |v| v.linlin(0.0, 2.0, 0, 1) },
			fromSlider: { |v| v.linlin(0, 1, 0.0, 2.0) },
			action: { |v| ctrl.setPartialParamAt(\partAtk, i, v) },
			decimals: 2
		);

		// Per-partial release
		StaticText(strip, Rect(2, 256, sl, 12))
			.string_("rel").stringColor_(txtCol).font_(KassiaPlatform.uiFont(8));
		this.prMakeSliderNb(strip,
			slRect: Rect(2, 268, sl, 10),
			nbRect: Rect(2, 280, sl, 14),
			initVal: 0.0,
			bg: ctrlCol,
			toSlider: { |v| v.linlin(0.0, 4.0, 0, 1) },
			fromSlider: { |v| v.linlin(0, 1, 0.0, 4.0) },
			action: { |v| ctrl.setPartialParamAt(\partRel, i, v) },
			decimals: 2
		);
	}

	// Noise strip — level slider only, no freq readout or AM controls
	prMakeNoiseStrip { |x0, stripW|
		var strip, sw, noiseIdx;
		sw       = stripW - 6;
		noiseIdx = ctrl.voices[0].num;  // index 8, after the 8 partials

		strip = CompositeView(mid, Rect(x0, 4, sw, 300));
		strip.background_(stripBg);

		StaticText(strip, Rect(2, 2, sw - 2, 14))
			.string_("noise").stringColor_(txtCol).font_(uiFont);

		StaticText(strip, Rect(2, 18, sw - 2, 12))
			.string_("pink").stringColor_(txtCol).font_(KassiaPlatform.uiFont(8));

		levelSl[noiseIdx] = Slider(strip, Rect(2, 34, 14, 162))
			.value_(0.0).background_(lvlCol)
			.action_({ |s| ctrl.set(\noiseAmp, s.value) });

		levelNb[noiseIdx] = NumberBox(strip, Rect(2, 198, sw - 2, 16))
			.decimals_(3).step_(0.001).value_(0.0).font_(uiFont)
			.action_({ |n|
				var lv = n.value.clip(0, 1);
				levelSl[noiseIdx].value = lv;
				ctrl.set(\noiseAmp, lv);
			});
	}

	// ------------------------------------------------------------------
	// Widget helpers
	// ------------------------------------------------------------------

	free {}

	prMakeSlider { |parent, rect, val, bg, action|
		^Slider(parent, rect)
			.value_(val)
			.background_(bg)
			.action_({ |sl| action.(sl.value) });
	}

	prMakeSliderNb { |parent, slRect, nbRect, initVal, bg,
	                  toSlider, fromSlider, action, decimals=3|
		var sl, nb, step;

		step = (10 ** decimals.neg);

		sl = Slider(parent, slRect)
			.value_(toSlider.(initVal))
			.background_(bg);

		nb = NumberBox(parent, nbRect)
			.decimals_(decimals).step_(step).font_(uiFont)
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
