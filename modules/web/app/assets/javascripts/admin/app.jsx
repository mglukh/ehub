/** @jsx React.DOM */
require(['react','admin/gate/List'], function (React, List) {

    //alert("loaded?" + React + " : " + Timer );

    //React.renderComponent(<Timer start={new Date()}/>, document.getElementById('content'));

    React.renderComponent(<List />, document.getElementById('content'));

});