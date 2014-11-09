/** @jsx React.DOM */
define(['react', 'sendOnlyMixin'], function (React, sendOnlyMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [sendOnlyMixin],

        getInitialState: function () {
            return {connected: false}
        },

        handleStop: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "stop", {});
        },
        handleStart: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "start", {});
        },

        render: function () {

            var button;
            if (this.props.state == 'active') {
                button =
                    <button type="button" className="btn btn-primary btn-sm" onClick={this.handleStop}>
                    stop
                    </button>;
            } else {
                button =
                    <button type="button" className="btn btn-primary btn-sm" onClick={this.handleStart}>
                    start
                    </button>;
            }

            return button;
        }
    });

});