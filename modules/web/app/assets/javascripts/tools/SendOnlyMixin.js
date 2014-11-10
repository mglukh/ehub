define(['wsclient'], function(client) {

    return {
        startListener: function () {
            var self = this;

            this.handle = client.getHandle();

            function wsOpenHandler() {
                self.setState({connected: true});
                console.debug("onConnected()" );
            }

            function wsClosedHandler() {
                self.setState({connected: false});
                console.debug("onDisconnected()");
            }

            this.handle.addWsOpenEventListener(wsOpenHandler);
            this.handle.addWsClosedEventListener(wsClosedHandler);

            this.sendCommand = this.handle.command;
        },

        stopListener: function () {
            if (this.handle) {
                this.handle.stop();
            }
        },
        componentDidMount: function() {
            this.startListener();
            if (this.onMount) {
                this.onMount();
            }
        },
        componentWillUnmount: function() {
            this.stopListener();
            if (this.onUnmount) {
                this.onUnmount();
            }
        }
    };

});