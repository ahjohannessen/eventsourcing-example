Note
----

Work on this branch ([play](https://github.com/krasserm/eventsourcing-example/tree/play)) replaces the [Jersey](http://jersey.java.net/) and [Scalate](http://scalate.fusesource.org/)-based web layer with a [Play Framework 2.0](https://github.com/playframework/Play20/) based implementation. Here's a summary of the current state:

* The RESTful XML/JSON API is partly implemented (see [Application](https://github.com/krasserm/eventsourcing-example/blob/play/app/controllers/Application.scala) controller)
* The JSON/XML (un)marshaling is based on [JAXB](http://jcp.org/en/jsr/detail?id=222) metadata (annotations on immutable domain classes) where the JAXB-based JSON processing is done using Jersey's [JSON library](http://jersey.java.net/nonav/documentation/latest/user-guide.html#d4e911). This is useful when you want to support both XML and JSON APIs (for the same resource model) but want to define the bindings with the same metadata. Play's native JSON and XML support is not used. 
* The [JaxbSupport](https://github.com/krasserm/eventsourcing-example/blob/play/app/support/JaxbSupport.scala#L18) trait implements Play-specific body parsers for unmarshalling XML and JSON documents to JAXB annotated domain objects. It also features typeclass-based rendering of XML and JSON responses including support for content negotiation based on the HTTP Accept header. The implementation is preliminary but should give you an idea in which direction the development goes.
* Asynchronous responses from write operations can now be fully leveraged using Play's [asynchronous HTTP programming model](https://github.com/playframework/Play20/wiki/ScalaAsync).
* HTML representations are not supported yet (but will be added soon). They are supported in the Jersey and Scalate-based version of the example application on branches [master](https://github.com/krasserm/eventsourcing-example) and [part-2](https://github.com/krasserm/eventsourcing-example/tree/part-2)

Further below you can find instructions how to run the example application together with some usage example of the RESTful XML and JSON API

Blog
----

<table>
    <tr>
        <td><b>Part</b></td>
        <td><b>Branch</b></td>
    </tr>
    <tr>
        <td><a href="http://krasserm.blogspot.com/2011/11/building-event-sourced-web-application.html">Building an Event-Sourced Web Application - Part 1: Domain Model, Events and State</a></td>
        <td><a href="https://github.com/krasserm/eventsourcing-example/tree/part-1">part-1</a></td>
    </tr>
    <tr>
        <td><a href="http://krasserm.blogspot.com/2012/01/building-event-sourced-web-application.html">Building an Event-Sourced Web Application - Part 2: Projections, Persistence, Consumers and Web Interface</a> </td>
        <td><a href="https://github.com/krasserm/eventsourcing-example/tree/part-2">part-2</a></td>
    </tr>
</table>

Run
---

    sbt run

This will start a web server listening on port 9000. You'll need sbt 0.11.2 to get it running.

Web API
-------

This section shows some examples how to use the RESTful XML and JSON API. Let's start by creating a new `draft-invoice` with id `123`:

    curl -v -H "Content-Type: text/xml" -d "<draft-invoice id='123'/>" http://localhost:9000/invoice

The result is an empty invoice located at /invoice/123 

    < HTTP/1.1 201 Created
    < Content-Type: text/xml; charset=utf-8
    < Location: /invoice/123
    < Content-Length: 296

    <invoices>
      <draft-invoice id="123" version="0">
        <total>0</total>
        <sum>0</sum>
        <discount>0</discount>
        <items/>
      </draft-invoice>
    </invoices>

The newly created invoice can now be fetched with

    curl -H "Accept: text/xml" http://localhost:9000/invoice/123

The returned XML representation is

    <draft-invoice id="123" version="0">
      <total>0</total>
      <sum>0</sum>
      <discount>0</discount>
      <items/>
    </draft-invoice>

An invoice item can be added (to the empty list of items of invoice 123) with

    curl -v -H "Content-Type: text/xml" -d "<item><description>item-1</description><count>2</count><amount>4.1</amount></item>" http://localhost:9000/invoice/123/item

The returned XML representation shows the item list of invoice 123 (where the Location header contains the path to the newly created invoice item)

    < HTTP/1.1 201 Created
    < Content-Type: text/xml; charset=utf-8
    < Location: /invoice/123/item/0
    < Content-Length: 152

    <items>
      <item>
        <description>item-1</description>
        <count>2</count>
        <amount>4.1</amount>
      </item>
    </items>

The updated XML representation of invoice 123, obtained with 

    curl -H "Accept: text/xml" http://localhost:9000/invoice/123

now looks like 

    <draft-invoice id="123" version="1">
      <total>8.2</total>
      <sum>8.2</sum>
      <discount>0</discount>
      <items>
        <item>
          <description>item-1</description>
          <count>2</count>
          <amount>4.1</amount>
        </item>
      </items>
    </draft-invoice>

A JSON representation of the same invoice resource can be obtained with 

    curl -H "Accept: application/json" http://localhost:9000/invoice/123

The result is

    {
     "draft-invoice":
     {
      "@id":"123",
      "@version":"1",
      "total":"8.2",
      "sum":"8.2",
      "discount":"0",
      "items":
      {
       "item":
       {
        "description":"item-1",
        "count":"2",
        "amount":"4.1"
       }
      }
     }
    }

Please note that you can even use different MIME types for the Content-Type and Accept headers in a single command. The following example appends another invoice item using a JSON representation but accepts responses in XML representation:

    curl -v -H "Content-Type: application/json" -H "Accept: text/xml" -d '{"item":{"description":"item-2","count":"1","amount":"3.4"}}' http://localhost:9000/invoice/123/item

The response is 

    < HTTP/1.1 201 Created
    < Content-Type: text/xml; charset=utf-8
    < Location: /invoice/123/item/1
    < Content-Length: 234

    <items>
      <item>
        <description>item-1</description>
        <count>2</count>
        <amount>4.1</amount>
      </item>
      <item>
        <description>item-2</description>
        <count>1</count>
        <amount>3.4</amount>
      </item>
    </items>

More documentation to follow ...