define(['ServerClient'], function(client) {

    return {
        subscribe: function() {
            var id = this.subscriptionId();
            if (id) {
                this.handle.subscribe(id);
            } else {
                console.warn("subscriptionId() is undefined or returns empty string");
            }
        },
        startSubscription: function () {
            var _this = this;
            this.listener = {
                onConnected: function () {
                    console.debug("onConnected() for " + _this.subscriptionId());
                    _this.subscribe();
                },
                onDisconnected: function () {
                    console.debug("onDisconnected() for " + _this.subscriptionId());
                },
                onMessage: function (data) {
                    console.debug("onMessage() for " + _this.subscriptionId());
                    _this.onDataUpdate(data);
                }
            };
            this.handle = client.addListener(this.listener);
            console.debug("Initiated subscription for " + this.subscriptionId());
        },
        stopSubscription: function () {
            this.handle.stop();
        }
    };

});