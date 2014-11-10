/** @jsx React.DOM */
define(['react', 'subscriberMixin', 'admin/agent/datatap/ListItem'], function (React, subscriberMixin, ListItem) {

    return React.createClass({
        mixins: [subscriberMixin],

        subscriptionConfig: function () {
            return {route:this.props.id, topic:'taps', target: 'list'};
        },
        getInitialState: function () {
            return {data: null}
        },

        renderData: function() {
            return (
                <div>
                {this.state.list.map(function (el) {
                    return <ListItem key={el.id} id={el.id}/>;
                    })}
                </div>
            );
        },
        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.list) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});