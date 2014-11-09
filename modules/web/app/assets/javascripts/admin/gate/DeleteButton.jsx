/** @jsx React.DOM */
define(['react', 'sendOnlyMixin'], function (React, sendOnlyMixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [sendOnlyMixin],

        getInitialState: function () {
            return {connected: false}
        },

        handleKill: function(e) {
            this.sendCommand(this.props.addr, this.props.route, "kill", {});
        },

        render: function () {

            var button =
                    <button type="button" className="btn btn-primary btn-sm" onClick={this.handleKill}>
                    delete
                    </button>;

            return button;
        }
    });

});