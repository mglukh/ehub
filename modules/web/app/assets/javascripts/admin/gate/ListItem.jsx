/** @jsx React.DOM */
define([
        'react',
        'subscriberMixin',
        'admin/gate/StartStopButton',
        'admin/gate/DeleteButton'
    ],
    function (React,
              subscriberMixin,
              StartStopButton,
              DeleteButton) {

        return React.createClass({
            mixins: [subscriberMixin],

            subscriptionConfig: function () {
                return {address: this.props.addr, route: this.props.id, topic: 'info', target: 'info'};
            },
            getInitialState: function () {
                return {info: null}
            },

            renderData: function () {
                return (
                    <div>
                    {this.state.info.name} : {this.state.info.text} :
                        <StartStopButton addr={this.props.addr} state={this.state.info.state} route={this.props.id} /> :
                        <DeleteButton addr={this.props.addr} route={this.props.id} /> :
                    </div>
                );
            },
            renderLoading: function () {
                return (
                    <div>loading...</div>
                );
            },

            render: function () {
                if (this.state.info) {
                    return this.renderData();
                } else {
                    return this.renderLoading();
                }
            }
        });

    });