/** @jsx React.DOM */
define(['react', 'admin/agent/datatap/List'],
    function (React, List) {

    return React.createClass({

        getInitialState: function () {
            return {}
        },

        render: function () {
            return (
                <div>
                    <List/>
                </div>
            )
        }
    });

});