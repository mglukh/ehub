define(['wsclient'], function (client) {

    return {
        onDataUpdate: function (key, data) {
            var partialStateUpdate = {};
            partialStateUpdate[key] = data;
            this.setState(partialStateUpdate);
        },
        onStaleUpdate: function (key, data) {
            var staleKey = key+"_stale";
            if (this.state[staleKey] != data) {
                var partialStateUpdate = {};
                partialStateUpdate[staleKey] = data;
                this.setState(partialStateUpdate);
            }
        },

        updateHandler: function (type, data) {
            var id = this.subscriptionConfig();
            console.debug("onMessage() type " + type + " for " + id.route + "{" + id.topic + "}");
            var staleKey = id.target+"_stale";
            if (type == "D") {
                this.onStaleUpdate(id.target, true);
            } else {
                this.onDataUpdate(id.target, data);
                this.onStaleUpdate(id.target, false);
            }
        },

        startSubscription: function () {
            var self = this;
            var id = this.subscriptionConfig();

            this.handle = client.getHandle();

            function componentId() {
                return id.route + "{" + id.topic + "}";
            }

            function subscribe() {
                if (id) {
                    self.handle.subscribe(id.route, id.topic, self.updateHandler);
                } else {
                    console.warn("subscriptionId is undefined or returns empty string");
                }
            }

            function wsOpenHandler() {
                self.setState({connected: true});
                console.debug("onConnected() for " + id);
                subscribe();
            }

            function wsClosedHandler() {
                self.setState({connected: false});
                console.debug("onDisconnected() for " + id);
            }

            this.handle.addWsOpenEventListener(wsOpenHandler);
            this.handle.addWsClosedEventListener(wsClosedHandler);

            this.sendCommand = this.handle.command;

            if (this.handle.connected) {
                wsOpenHandler();
                subscribe();
            } else {
                wsClosedHandler();
            }

            console.debug("Initiated subscription for " + id);
        },
        stopSubscription: function () {
            if (this.handle) {
                var id = this.subscriptionConfig();

                this.handle.unsubscribe(id.route, id.topic, this.updateHandler);

                this.handle.stop();
                this.handle = null;
            }
        },
        componentDidMount: function () {
            this.startSubscription();
            if (this.onMount) {
                this.onMount();
            }
        },
        componentWillUnmount: function () {
            this.stopSubscription();
            if (this.onUnmount) {
                this.onUnmount();
            }
        }
    };

});