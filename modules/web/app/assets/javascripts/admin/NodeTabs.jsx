/** @jsx React.DOM */
define(['react', 'subscriberMixin', 'admin/AdminContainer'], function (React, subscriberMixin, Container) {

    return React.createClass({
        mixins: [subscriberMixin],

        subscriptionConfig: function () {
            return {route:'cluster', topic:'nodes', target: 'nodes'};
        },
        getInitialState: function () {
            return {nodes: null}
        },

        renderData: function() {

            var cx = React.addons.classSet;
            var connected = this.state.connected;

            return (
                <div>
                    <ul className="nav nav-tabs" role="tablist">
                        {this.state.nodes.map(function (el) {

                            var tabClasses = cx({
                                'disabled': (!connected || el.state != 'up'),
                                'active': $.inArray('adminweb', el.roles) > -1
                            });

                            return  <li key={el.id} role="presentation" className={tabClasses}><a href="#">{el.address} ({el.state})</a></li>;
                            })}
                    </ul>
                    <Container />
                </div>
            );
        },
        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.nodes) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});