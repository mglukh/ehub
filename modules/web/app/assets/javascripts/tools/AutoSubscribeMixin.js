define(['wsclient'], function(client) {

    return {
        subscribe: function(handle) {
            var id = this.subscriptionId();
            if (id) {
                handle.subscribe(id);
            } else {
                console.warn("subscriptionId() is undefined or returns empty string");
            }
        },
        startSubscription: function () {
            var self = this;
            this.listener = {
                onConnected: function (handle) {
                    console.debug("onConnected() for " + self.subscriptionId());
                    self.subscribe(handle);
                },
                onDisconnected: function (handle) {
                    console.debug("onDisconnected() for " + self.subscriptionId());
                },
                onMessage: function (data) {
                    console.debug("onMessage() for " + self.subscriptionId());
                    self.onDataUpdate(data);
                }
            };
            this.handle = client.addListener(this.listener);
            console.debug("Initiated subscription for " + this.subscriptionId());
        },
        stopSubscription: function () {
            this.handle.stop();
        },
        componentDidMount: function() {
            this.startSubscription();
            if (this.onMount) {
                this.onMount();
            }
        },
        componentWillUnmount: function() {
            this.stopSubscription();
            if (this.onUnmount) {
                this.onUnmount();
            }
        }
    };

});