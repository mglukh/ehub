/** @jsx React.DOM */
define(['react', 'subscriberMixin', 'admin/gate/ListItem'], function (React, subscriberMixin, ListItem) {

    return React.createClass({
        mixins: [subscriberMixin],

        subscriptionId: function () {
            return "/gates/list";
        },
        getInitialState: function () {
            return {data: null}
        },

        renderData: function() {
            return (
                <div>
                {this.state.data.map(function (el) {
                    return <ListItem id={el.id}/>;
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
            if (this.state.data) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});