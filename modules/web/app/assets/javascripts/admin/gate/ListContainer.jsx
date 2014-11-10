/** @jsx React.DOM */
define(['react', 'admin/gate/List', 'admin/gate/AddButton'],
    function (React, List, AddButton) {

    return React.createClass({

        getInitialState: function () {
            return {}
        },

        render: function () {
            return (
                <div>
                    <AddButton />
                    <List/>
                </div>
            )
        }
    });

});