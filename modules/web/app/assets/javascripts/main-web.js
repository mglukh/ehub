(function() {
    require.config({

        /*noinspection */
        paths: {
            common: "../lib/common/javascripts",
            jquery: "../lib/jquery/jquery",
            react: "../lib/react/react-with-addons",
            bootstrap: "../lib/bootstrap/js/bootstrap",

            subscriberMixin: "tools/AutoSubscribeMixin",
            sendOnlyMixin: "tools/SendOnlyMixin",
            wsclient: "tools/ServerClient"
        },
        shim: {
            bootstrap: {
                deps: ["jquery"]
            },
            jquery: {
                exports: "$"
            }
        }
    });

    require(["admin/app"]);

})();