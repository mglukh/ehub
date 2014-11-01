define(['wsclient'], function(client) {

    return {
        subscribe: function(handle) {
            var id = this.subscriptionId();
            if (id) {
                handle.subscribe(id);
            } else {
                console.warn("subscriptionId is undefined or returns empty string");
            }
        },
        onDataUpdate: function (data) {
            this.setState({data: data});
        },
        startSubscription: function () {
            var self = this;
            var id = this.subscriptionId();
            this.listener = {
                onConnected: function (handle) {
                    self.setState({connected: true});
                    console.debug("onConnected() for " + id);
                    self.subscribe(handle);
                },
                onDisconnected: function (handle) {
                    self.setState({connected: false});
                    console.debug("onDisconnected() for " + id);
                },
                onMessage: function (data) {
                    console.debug("onMessage() for " + id);
                    self.onDataUpdate(data);
                }
            };
            this.handle = client.addListener(this.listener);
            console.debug("Initiated subscription for " + id);
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