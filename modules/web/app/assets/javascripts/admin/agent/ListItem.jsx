/** @jsx React.DOM */
define([
        'react',
        'subscriberMixin',
        'admin/gate/StartStopButton',
        'admin/agent/datatap/ListContainer'
    ],
    function (React,
              subscriberMixin,
              StartStopButton,
              TapListContainer) {

        return React.createClass({
            mixins: [subscriberMixin],

            subscriptionConfig: function () {
                return {route: this.props.id, topic: 'info', target: 'info'};
            },
            getInitialState: function () {
                return {info: null, selected: false}
            },

            renderData: function () {
                return (
                    <div>
                    <a href="#" onClick={this.props.handleSelection(this.props.id)}>{this.state.info.name}</a> {this.state.info_stale ? "Stale" : "not stale"} : {this.state.info.text} :
                        <StartStopButton state={this.state.info.state} route={this.props.id} />
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