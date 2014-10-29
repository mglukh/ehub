require.config {
	paths: {
		common: "../lib/common/javascripts"
		jquery: "../lib/jquery/jquery"
		react: "../lib/react/react"
		bootstrap: "../lib/bootstrap/js/bootstrap"
	}
	shim: {
		bootstrap: {
			deps: ["jquery"]
		}
		jquery: {
			exports: "$"
		}
	}
}

require ["attempt1"]