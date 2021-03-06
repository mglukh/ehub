/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['wsclient'], function (client) {

    return {



        onDataUpdate: function (key, data) {
            var partialStateUpdate = {};
            partialStateUpdate[key] = data;
            this.setState(partialStateUpdate);
        },
        onStaleUpdate: function (key, data) {
            var id = this.subscriptionConfig();
            console.debug("Stale: "+data+" for " + key +" on " + id.route + "{" + id.topic + "}@" + id.address);
            var staleKey = key+"_stale";
            if (this.state[staleKey] != data) {
                var partialStateUpdate = {};
                partialStateUpdate[staleKey] = data;
                this.setState(partialStateUpdate);
            }
        },

        updateHandler: function (type, data) {
            var id = this.subscriptionConfig();
            console.debug("onMessage() type " + type + " for " + id.route + "{" + id.topic + "}@" + id.address);
            var staleKey = id.target+"_stale";
            if (type == "D") {
                this.onStaleUpdate(id.target, true);
            } else {
                this.onDataUpdate(id.target, data);
                this.onStaleUpdate(id.target, false);
            }
        },

        startListener: function () {
            var self = this;
            var id = this.subscriptionConfig();

            this.handle = client.getHandle();

            function componentId() {
                return id.route + "{" + id.topic + "}@" + id.address;
            }

            function subscribe() {
                if (id) {
                    self.handle.subscribe(id.address, id.route, id.topic, self.updateHandler);
                } else {
                    console.warn("subscriptionId is undefined or returns empty string");
                }
            }

            function wsOpenHandler() {
                self.setState({connected: true});
                console.debug("onConnected() for " + componentId());
                subscribe();
            }

            function wsClosedHandler() {
                self.setState({connected: false});
                console.debug("onDisconnected() for " + componentId());
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

            console.debug("Initiated subscription for " + componentId());
        },

        stopListener: function () {
            if (this.handle) {
                var id = this.subscriptionConfig();

                this.handle.unsubscribe(id.address, id.route, id.topic, this.updateHandler);

                this.handle.stop();
                this.handle = null;
            }
        }
    };

});