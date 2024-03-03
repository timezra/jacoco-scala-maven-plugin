package org.acme

case class CompareService() extends EndPhrases[String] {
    def comparing(name: String) = {
        "let's compare to " + name + ok();
    }
    override def toString(): String = {
        var rval = "do some reflection magic here"
        rval += "string 1"
        rval += "string 2"
        rval += "string 3"
        return rval
    }

    override def ok(): String = ", ok?"
    override def realquick(): String = ", real quick!"
}
