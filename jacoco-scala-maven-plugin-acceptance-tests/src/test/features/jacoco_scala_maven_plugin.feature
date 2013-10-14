#encoding: utf-8
 
Feature: Showcase the jacoco-scala-maven-plugin integration
  In order to verify that the jacoco-scala-maven-plugin works
  As someone who wants to generate scala test coverage reports through maven 
  I should be able to run this scenario and see that the steps pass
 
  Background:
    Given a local repository '${local-repository}'
  
  Scenario: Excludes Mixins
    Given a scala project '${project.build.testOutputDirectory}/it-excludes-mixins' with mixins filtered
    When I verify it
    Then mixed-in methods should not be in the coverage report

  Scenario: Excludes Case Methods
    Given a scala project '${project.build.testOutputDirectory}/it-excludes-case-methods' with case methods filtered
    When I verify it
    Then case methods should not be in the coverage report

  Scenario: Excludes Filters
    Given a scala project '${project.build.testOutputDirectory}/it-excludes-filters' without filters
    When I verify it
    Then mixed-in methods should be in the coverage report
    And case methods should be in the coverage report

  Scenario: Excludes Everything
    Given a scala project '${project.build.testOutputDirectory}/it-excludes-everything' with everything filtered
    When I verify it
    Then mixed-in methods should not be in the coverage report
    And case methods should not be in the coverage report
    
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