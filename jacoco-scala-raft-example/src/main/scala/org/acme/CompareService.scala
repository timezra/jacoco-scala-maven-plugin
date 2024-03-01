package org.acme

case class CompareService() {
    def comparing(name: String) = {
        "let's compare " + name;
    }
    override def toString(): String = {
        var rval = "do some reflection magic here"
        rval += "string 1"
        rval += "string 2"
        rval += "string 3"
        return rval
    }
}
