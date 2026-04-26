// KassiaPlatform.sc
// Platform utility helpers shared across the Kassia suite.
// Handles font names and default paths in a cross-platform way.
//
// Usage:
//   KassiaPlatform.uiFont(10)   -> Font for labels
//   KassiaPlatform.btnFont(8)   -> Font for buttons
//   KassiaPlatform.presetsDir   -> default directory for preset files

KassiaPlatform {

	// Returns the best available sans-serif font for the current platform.
	// Liberation Sans is Linux-only; Mac uses Helvetica; Windows uses Arial.
	*sansFontName {
		^switch(thisProcess.platform.name)
			{ \linux   } { "Liberation Sans" }
			{ \osx     } { "Helvetica"        }
			{ \windows } { "Arial"            }
			/* fallback */ { "Helvetica"       }
	}

	*uiFont { |size=10|
		^Font(this.sansFontName, size)
	}

	*btnFont { |size=8|
		^Font(this.sansFontName, size)
	}

	// Default directory for saving/loading preset files.
	// Uses ~/sc-patches/presets/<instrument> to keep presets
	// visible and co-located with the SC working directory.
	*presetsDir { |instrument|
		^Platform.userHomeDir ++ "/sc-patches/presets/" ++ instrument
	}

	// Ensures the presets directory exists, creating it if necessary.
	// Returns the path.
	*ensurePresetsDir { |instrument|
		var dir = this.presetsDir(instrument);
		if(File.exists(dir).not) {
			File.mkdir(dir);
		};
		^dir
	}

}
