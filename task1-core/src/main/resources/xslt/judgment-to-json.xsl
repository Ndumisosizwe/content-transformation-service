<?xml version="1.0" encoding="UTF-8"?>
<!--
    XSLT 3.0 stylesheet to transform legal XML judgments (namespace urn:lex:content:1)
    into normalized JSON suitable for search and RAG pipelines.
    
    Executed by Saxon-HE.
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:lex="urn:lex:content:1"
                exclude-result-prefixes="lex">

    <xsl:output method="text" encoding="UTF-8"/>

    <!-- Entry point: match the root judgment element -->
    <xsl:template match="/lex:judgment">
        <xsl:text>{</xsl:text>

        <!-- content_id -->
        <xsl:text>"content_id": "</xsl:text>
        <xsl:value-of select="lex:header/lex:content_id"/>
        <xsl:text>",</xsl:text>

        <!-- title -->
        <xsl:text>"title": "</xsl:text>
        <xsl:call-template name="escape-json">
            <xsl:with-param name="text" select="lex:header/lex:title"/>
        </xsl:call-template>
        <xsl:text>",</xsl:text>

        <!-- court -->
        <xsl:text>"court": "</xsl:text>
        <xsl:call-template name="escape-json">
            <xsl:with-param name="text" select="lex:header/lex:court"/>
        </xsl:call-template>
        <xsl:text>",</xsl:text>

        <!-- jurisdiction -->
        <xsl:text>"jurisdiction": "</xsl:text>
        <xsl:value-of select="lex:header/lex:jurisdiction"/>
        <xsl:text>",</xsl:text>

        <!-- decision_date -->
        <xsl:text>"decision_date": "</xsl:text>
        <xsl:value-of select="lex:header/lex:decision_date"/>
        <xsl:text>",</xsl:text>

        <!-- citations array -->
        <xsl:text>"citations": [</xsl:text>
        <xsl:for-each select="lex:header/lex:citations/lex:citation">
            <xsl:if test="position() > 1">,</xsl:if>
            <xsl:text>{"type": "</xsl:text>
            <xsl:value-of select="@type"/>
            <xsl:text>", "value": "</xsl:text>
            <xsl:call-template name="escape-json">
                <xsl:with-param name="text" select="."/>
            </xsl:call-template>
            <xsl:text>"}</xsl:text>
        </xsl:for-each>
        <xsl:text>],</xsl:text>

        <!-- parties array -->
        <xsl:text>"parties": [</xsl:text>
        <xsl:for-each select="lex:header/lex:parties/lex:party">
            <xsl:if test="position() > 1">,</xsl:if>
            <xsl:text>{"role": "</xsl:text>
            <xsl:value-of select="@role"/>
            <xsl:text>", "name": "</xsl:text>
            <xsl:call-template name="escape-json">
                <xsl:with-param name="text" select="."/>
            </xsl:call-template>
            <xsl:text>"}</xsl:text>
        </xsl:for-each>
        <xsl:text>],</xsl:text>

        <!-- paragraphs array -->
        <xsl:text>"paragraphs": [</xsl:text>
        <xsl:for-each select="lex:body/lex:section/lex:p">
            <xsl:if test="position() > 1">,</xsl:if>
            <xsl:text>{"id": "</xsl:text>
            <xsl:value-of select="@id"/>
            <xsl:text>", "section": "</xsl:text>
            <xsl:value-of select="../@type"/>
            <xsl:text>", "text": "</xsl:text>
            <xsl:call-template name="escape-json">
                <xsl:with-param name="text" select="."/>
            </xsl:call-template>
            <xsl:text>"}</xsl:text>
        </xsl:for-each>
        <xsl:text>],</xsl:text>

        <!-- full_text: concatenated paragraphs for RAG -->
        <xsl:text>"full_text": "</xsl:text>
        <xsl:for-each select="lex:body/lex:section/lex:p">
            <xsl:if test="position() > 1">
                <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:call-template name="escape-json">
                <xsl:with-param name="text" select="."/>
            </xsl:call-template>
        </xsl:for-each>
        <xsl:text>"</xsl:text>

        <xsl:text>}</xsl:text>
    </xsl:template>

    <!--
        Named template to escape special characters for JSON string values.
        Handles: backslash, double quote, newline, carriage return, tab.
    -->
    <xsl:template name="escape-json">
        <xsl:param name="text"/>
        <xsl:variable name="step1" select="replace($text, '\\', '\\\\')"/>
        <xsl:variable name="step2" select="replace($step1, '&quot;', '\\&quot;')"/>
        <xsl:variable name="step3" select="replace($step2, '&#10;', '\\n')"/>
        <xsl:variable name="step4" select="replace($step3, '&#13;', '\\r')"/>
        <xsl:variable name="step5" select="replace($step4, '&#9;', '\\t')"/>
        <xsl:value-of select="$step5"/>
    </xsl:template>

</xsl:stylesheet>
