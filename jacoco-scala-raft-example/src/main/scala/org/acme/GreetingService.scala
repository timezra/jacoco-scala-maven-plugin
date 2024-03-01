package org.acme

case class GreetingService() {
    def greeting(name: String) = {
        "hello " + name;
    }
}

