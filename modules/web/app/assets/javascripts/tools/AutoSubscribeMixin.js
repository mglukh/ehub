define(['wsclient'], function (client) {

    return {
        onDataUpdate: function (key, data) {
            var partialStateUpdate = {};
            partialStateUpdate[key] = data;
            this.setState(partialStateUpdate);
        },
        startSubscription: function () {
            var self = this;
            var id = this.subscriptionConfig();

            this.handle = client.getHandle();

            function componentId() {
                return id.route + "{" + id.topic + "}";
            }

            function updateHandler(data) {
                console.debug("onMessage() for " + id);
                self.onDataUpdate(id.target, data);
            }

            function subscribe() {
                if (id) {
                    self.handle.subscribe(id.route, id.topic, updateHandler);
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
                subscribe();
            }

            console.debug("Initiated subscription for " + id);
        },
        stopSubscription: function () {
            if (this.handle) {
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