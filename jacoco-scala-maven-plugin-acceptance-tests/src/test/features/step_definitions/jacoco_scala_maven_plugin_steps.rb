#encoding: utf-8

require 'cucumber/api/jruby/en'
require 'rspec/expectations'
World(RSpec::Matchers)

Given /^a local repository '(.*)'$/ do |repo|
  @repo = repo
end

Given /^a scala project '(.*)' (?:with|without) mixins filtered$/ do |project|
  @project = project
end

When /^I (.*) it$/ do |goal|
  Dir.chdir(@project) do
    mvn goal
  end
end

Then /^mixed-in trait methods (should|should not) be in the coverage report$/ do |should_or_should_not|
  report = IO.read("#{@project}/target/site/jacoco/default/Example.html")
  report.send _(should_or_should_not), match(/class\="el_method">thisIsMixedIn\(\)/)
end

def mvn (goal, params = {})
  params['maven.repo.local'] = @repo
  command = params.reduce("mvn -N -B #{goal}") {|s, (k, v)|
    s + " -D#{k}=\"#{v}\""
  }
  `#{command}`
end

def _ (words)
  words.gsub(' ', '_').to_sym
end

# ##############################################################################
# Copyright (c) 2013 timezra
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
# ##############################################################################
