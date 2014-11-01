/** @jsx React.DOM */
require(['react','admin/gate/ListContainer'], function (React, ListContainer) {

    //alert("loaded?" + React + " : " + Timer );

    //React.renderComponent(<Timer start={new Date()}/>, document.getElementById('content'));

    React.renderComponent(<ListContainer />, document.getElementById('content'));

});