define(['wsclient'], function(client) {

    return {
        startListener: function () {
            var self = this;
            this.listener = {
                onConnected: function (handle) {
                    self.setState({connected: true});
                },
                onDisconnected: function (handle) {
                    self.setState({connected: false});
                },
                onMessage: function (data) {
                }
            };
            this.handle = client.addListener(this.listener);
            this.sendCommand = this.handle.command;
        },
        stopListener: function () {
            this.handle.stop();
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