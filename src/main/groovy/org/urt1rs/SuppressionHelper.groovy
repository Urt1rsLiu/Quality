package org.urt1rs


import org.w3c.dom.DOMImplementation
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.Transformer
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class SuppressionHelper {

    File suppression

    DocumentBuilder docBuilder

    Document doc

    String DOCTYPE_HEADER = "<!DOCTYPE suppressions PUBLIC\n" +
            "    \"-//Puppy Crawl//DTD Suppressions 1.1//EN\"\n" +
            "    \"http://www.puppycrawl.com/dtds/suppressions_1_1.dtd\"\n>"

    static final String ATTRIBUTE_CHECKS = "checks"
    static final String ATTRIBUTE_FILES = "files"

    SuppressionHelper(File suppression) {
        this.suppression = suppression

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance()
        docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        docBuilder = docFactory.newDocumentBuilder()
        doc = docBuilder.parse(suppression)
    }

    boolean appendSuppression(File append) {
//        XmlParser parser = new XmlParser()
//        //禁用下载外部dtd解析xml，按普通xml文件格式读取
//        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
//        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        Document appendDoc = docBuilder.parse(append)
        if (null == appendDoc) {
            return false
        }
        NodeList appendSups = appendDoc.getElementsByTagName('suppress')
        //直接插入suppress节点，可能会存在重复情况
        for (int i = 0; i < appendSups.length; i++) {
            doc.getElementsByTagName("suppressions").item(0).appendChild(doc.importNode(appendSups.item(i), true))
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance()
        transformerFactory.setAttribute("indent-number", 2)
        Transformer transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes")
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        DOMImplementation domImpl = doc.getImplementation()
        DocumentType doctype = domImpl.createDocumentType("suppressions",
                "-//Puppy Crawl//DTD Suppressions 1.1//EN",
                "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd")
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype.getPublicId())
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctype.getSystemId())
        DOMSource source = new DOMSource(doc)
        StreamResult result = new StreamResult(suppression)
        transformer.transform(source, result)
    }


}
