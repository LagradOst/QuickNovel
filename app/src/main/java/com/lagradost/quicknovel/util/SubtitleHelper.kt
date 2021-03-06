package com.lagradost.quicknovel.util

//yoinked from cloudstream-3

import java.util.*

object SubtitleHelper {
    data class Language639(
        val languageName: String,
        val nativeName: String,
        val ISO_639_1: String,
        val ISO_639_2_T: String,
        val ISO_639_2_B: String,
        val ISO_639_3: String,
        val ISO_639_6: String,
    )

    /** lang -> ISO_639_1*/
    fun fromLanguageToTwoLetters(input: String): String? {
        for (lang in languages) {
            if (lang.languageName == input || lang.nativeName == input) {
                return lang.ISO_639_1
            }
        }
        return null
    }

    /** ISO_639_1 -> lang*/
    fun fromTwoLettersToLanguage(input: String): String? {
        if (input.length != 2) return null
        val comparison = input.lowercase(Locale.ROOT)
        for (lang in languages) {
            if (lang.ISO_639_1 == comparison) {
                return lang.languageName
            }
        }
        return null
    }

    /**ISO_639_2_B or ISO_639_2_T or ISO_639_3-> lang*/
    fun fromThreeLettersToLanguage(input: String): String? {
        if (input.length != 3) return null
        val comparison = input.lowercase(Locale.ROOT)
        for (lang in languages) {
            if (lang.ISO_639_2_B == comparison) {
                return lang.languageName
            }
        }
        for (lang in languages) {
            if (lang.ISO_639_2_T == comparison) {
                return lang.languageName
            }
        }
        for (lang in languages) {
            if (lang.ISO_639_3 == comparison) {
                return lang.languageName
            }
        }
        return null
    }

    /** lang -> ISO_639_2_T*/
    fun fromLanguageToThreeLetters(input: String): String? {
        for (lang in languages) {
            if (lang.languageName == input || lang.nativeName == input) {
                return lang.ISO_639_2_T
            }
        }
        return null
    }

    val languages = listOf(
        Language639("Abkhaz", "?????????? ????????????, ????????????", "ab", "abk", "abk", "abk", "abks"),
        Language639("Afar", "Afaraf", "aa", "aar", "aar", "aar", "aars"),
        Language639("Afrikaans", "Afrikaans", "af", "afr", "afr", "afr", "afrs"),
        Language639("Akan", "Akan", "ak", "aka", "aka", "aka", ""),
        Language639("Albanian", "Shqip", "sq", "sqi", "", "sqi", ""),
        Language639("Amharic", "????????????", "am", "amh", "amh", "amh", ""),
        Language639("Arabic", "??????????????", "ar", "ara", "ara", "ara", ""),
        Language639("Aragonese", "aragon??s", "an", "arg", "arg", "arg", ""),
        Language639("Armenian", "??????????????", "hy", "hye", "", "hye", ""),
        Language639("Assamese", "?????????????????????", "as", "asm", "asm", "asm", ""),
        Language639("Avaric", "???????? ????????, ???????????????? ????????", "av", "ava", "ava", "ava", ""),
        Language639("Avestan", "avesta", "ae", "ave", "ave", "ave", ""),
        Language639("Aymara", "aymar aru", "ay", "aym", "aym", "aym", ""),
        Language639("Azerbaijani", "az??rbaycan dili", "az", "aze", "aze", "aze", ""),
        Language639("Bambara", "bamanankan", "bm", "bam", "bam", "bam", ""),
        Language639("Bashkir", "?????????????? ????????", "ba", "bak", "bak", "bak", ""),
        Language639("Basque", "euskara, euskera", "eu", "eus", "", "eus", ""),
        Language639("Belarusian", "???????????????????? ????????", "be", "bel", "bel", "bel", ""),
        Language639("Bengali", "???????????????", "bn", "ben", "ben", "ben", ""),
        Language639("Bihari", "?????????????????????", "bh", "bih", "bih", "", ""),
        Language639("Bislama", "Bislama", "bi", "bis", "bis", "bis", ""),
        Language639("Bosnian", "bosanski jezik", "bs", "bos", "bos", "bos", "boss"),
        Language639("Breton", "brezhoneg", "br", "bre", "bre", "bre", ""),
        Language639("Bulgarian", "?????????????????? ????????", "bg", "bul", "bul", "bul", "buls"),
        Language639("Burmese", "???????????????", "my", "mya", "", "mya", ""),
        Language639("Catalan", "catal??", "ca", "cat", "cat", "cat", ""),
        Language639("Chamorro", "Chamoru", "ch", "cha", "cha", "cha", ""),
        Language639("Chechen", "?????????????? ????????", "ce", "che", "che", "che", ""),
        Language639("Chichewa", "chiChe??a, chinyanja", "ny", "nya", "nya", "nya", ""),
        Language639("Chinese", "?????? (Zh??ngw??n), ??????, ??????", "zh", "zho", "", "zho", ""),
        Language639("Chuvash", "?????????? ??????????", "cv", "chv", "chv", "chv", ""),
        Language639("Cornish", "Kernewek", "kw", "cor", "cor", "cor", ""),
        Language639("Corsican", "corsu, lingua corsa", "co", "cos", "cos", "cos", ""),
        Language639("Cree", "?????????????????????", "cr", "cre", "cre", "cre", ""),
        Language639("Croatian", "hrvatski jezik", "hr", "hrv", "hrv", "hrv", ""),
        Language639("Czech", "??e??tina, ??esk?? jazyk", "cs", "ces", "", "ces", ""),
        Language639("Danish", "dansk", "da", "dan", "dan", "dan", ""),
        Language639("Divehi", "????????????", "dv", "div", "div", "div", ""),
        Language639("Dutch", "Nederlands, Vlaams", "nl", "nld", "", "nld", ""),
        Language639("Dzongkha", "??????????????????", "dz", "dzo", "dzo", "dzo", ""),
        Language639("English", "English", "en", "eng", "eng", "eng", "engs"),
        Language639("Esperanto", "Esperanto", "eo", "epo", "epo", "epo", ""),
        Language639("Estonian", "eesti, eesti keel", "et", "est", "est", "est", ""),
        Language639("Ewe", "E??egbe", "ee", "ewe", "ewe", "ewe", ""),
        Language639("Faroese", "f??royskt", "fo", "fao", "fao", "fao", ""),
        Language639("Fijian", "vosa Vakaviti", "fj", "fij", "fij", "fij", ""),
        Language639("Finnish", "suomi, suomen kieli", "fi", "fin", "fin", "fin", ""),
        Language639("French", "fran??ais, langue fran??aise", "fr", "fra", "", "fra", "fras"),
        Language639("Fula", "Fulfulde, Pulaar, Pular", "ff", "ful", "ful", "ful", ""),
        Language639("Galician", "galego", "gl", "glg", "glg", "glg", ""),
        Language639("Georgian", "?????????????????????", "ka", "kat", "", "kat", ""),
        Language639("German", "Deutsch", "de", "deu", "", "deu", "deus"),
        Language639("Greek", "????????????????", "el", "ell", "", "ell", "ells"),
        Language639("Guaran??", "Ava??e'???", "gn", "grn", "grn", "grn", ""),
        Language639("Gujarati", "?????????????????????", "gu", "guj", "guj", "guj", ""),
        Language639("Haitian", "Krey??l ayisyen", "ht", "hat", "hat", "hat", ""),
        Language639("Hausa", "(Hausa) ????????????", "ha", "hau", "hau", "hau", ""),
        Language639("Hebrew", "??????????", "he", "heb", "heb", "heb", ""),
        Language639("Herero", "Otjiherero", "hz", "her", "her", "her", ""),
        Language639("Hindi", "??????????????????, ???????????????", "hi", "hin", "hin", "hin", "hins"),
        Language639("Hiri Motu", "Hiri Motu", "ho", "hmo", "hmo", "hmo", ""),
        Language639("Hungarian", "magyar", "hu", "hun", "hun", "hun", ""),
        Language639("Interlingua", "Interlingua", "ia", "ina", "ina", "ina", ""),
        Language639("Indonesian", "Bahasa Indonesia", "id", "ind", "ind", "ind", ""),
        Language639(
            "Interlingue",
            "Originally called Occidental; then Interlingue after WWII",
            "ie",
            "ile",
            "ile",
            "ile",
            ""
        ),
        Language639("Irish", "Gaeilge", "ga", "gle", "gle", "gle", ""),
        Language639("Igbo", "As???s??? Igbo", "ig", "ibo", "ibo", "ibo", ""),
        Language639("Inupiaq", "I??upiaq, I??upiatun", "ik", "ipk", "ipk", "ipk", ""),
        Language639("Ido", "Ido", "io", "ido", "ido", "ido", "idos"),
        Language639("Icelandic", "??slenska", "is", "isl", "", "isl", ""),
        Language639("Italian", "italiano", "it", "ita", "ita", "ita", "itas"),
        Language639("Inuktitut", "??????????????????", "iu", "iku", "iku", "iku", ""),
        Language639("Japanese", "????????? (????????????)", "ja", "jpn", "jpn", "jpn", ""),
        Language639("Javanese", "????????????", "jv", "jav", "jav", "jav", ""),
        Language639("Kalaallisut", "kalaallisut, kalaallit oqaasii", "kl", "kal", "kal", "kal", ""),
        Language639("Kannada", "???????????????", "kn", "kan", "kan", "kan", ""),
        Language639("Kanuri", "Kanuri", "kr", "kau", "kau", "kau", ""),
        Language639("Kashmiri", "?????????????????????, ???????????????", "ks", "kas", "kas", "kas", ""),
        Language639("Kazakh", "?????????? ????????", "kk", "kaz", "kaz", "kaz", ""),
        Language639("Khmer", "???????????????, ????????????????????????, ???????????????????????????", "km", "khm", "khm", "khm", ""),
        Language639("Kikuyu", "G??k??y??", "ki", "kik", "kik", "kik", ""),
        Language639("Kinyarwanda", "Ikinyarwanda", "rw", "kin", "kin", "kin", ""),
        Language639("Kyrgyz", "????????????????, ???????????? ????????", "ky", "kir", "kir", "kir", ""),
        Language639("Komi", "???????? ??????", "kv", "kom", "kom", "kom", ""),
        Language639("Kongo", "Kikongo", "kg", "kon", "kon", "kon", ""),
        Language639("Korean", "?????????, ?????????", "ko", "kor", "kor", "kor", ""),
        Language639("Kurdish", "Kurd??, ?????????????", "ku", "kur", "kur", "kur", ""),
        Language639("Kwanyama", "Kuanyama", "kj", "kua", "kua", "kua", ""),
        Language639("Latin", "latine, lingua latina", "la", "lat", "lat", "lat", "lats"),
        Language639("Luxembourgish", "L??tzebuergesch", "lb", "ltz", "ltz", "ltz", ""),
        Language639("Ganda", "Luganda", "lg", "lug", "lug", "lug", ""),
        Language639("Limburgish", "Limburgs", "li", "lim", "lim", "lim", ""),
        Language639("Lingala", "Ling??la", "ln", "lin", "lin", "lin", ""),
        Language639("Lao", "?????????????????????", "lo", "lao", "lao", "lao", ""),
        Language639("Lithuanian", "lietuvi?? kalba", "lt", "lit", "lit", "lit", ""),
        Language639("Luba-Katanga", "Tshiluba", "lu", "lub", "lub", "lub", ""),
        Language639("Latvian", "latvie??u valoda", "lv", "lav", "lav", "lav", ""),
        Language639("Manx", "Gaelg, Gailck", "gv", "glv", "glv", "glv", ""),
        Language639("Macedonian", "???????????????????? ??????????", "mk", "mkd", "", "mkd", ""),
        Language639("Malagasy", "fiteny malagasy", "mg", "mlg", "mlg", "mlg", ""),
        Language639("Malay", "bahasa Melayu, ???????? ?????????????", "ms", "msa", "", "msa", ""),
        Language639("Malayalam", "??????????????????", "ml", "mal", "mal", "mal", ""),
        Language639("Maltese", "Malti", "mt", "mlt", "mlt", "mlt", ""),
        Language639("M??ori", "te reo M??ori", "mi", "mri", "", "mri", ""),
        Language639("Marathi", "???????????????", "mr", "mar", "mar", "mar", ""),
        Language639("Marshallese", "Kajin M??aje??", "mh", "mah", "mah", "mah", ""),
        Language639("Mongolian", "???????????? ??????", "mn", "mon", "mon", "mon", ""),
        Language639("Nauruan", "Dorerin Naoero", "na", "nau", "nau", "nau", ""),
        Language639("Navajo", "Din?? bizaad", "nv", "nav", "nav", "nav", ""),
        Language639("Northern Ndebele", "isiNdebele", "nd", "nde", "nde", "nde", ""),
        Language639("Nepali", "??????????????????", "ne", "nep", "nep", "nep", ""),
        Language639("Ndonga", "Owambo", "ng", "ndo", "ndo", "ndo", ""),
        Language639("Norwegian Bokm??l", "Norsk bokm??l", "nb", "nob", "nob", "nob", ""),
        Language639("Norwegian Nynorsk", "Norsk nynorsk", "nn", "nno", "nno", "nno", ""),
        Language639("Norwegian", "Norsk", "no", "nor", "nor", "nor", ""),
        Language639("Nuosu", "????????? Nuosuhxop", "ii", "iii", "iii", "iii", ""),
        Language639("Southern Ndebele", "isiNdebele", "nr", "nbl", "nbl", "nbl", ""),
        Language639("Occitan", "occitan, lenga d'??c", "oc", "oci", "oci", "oci", ""),
        Language639("Ojibwe", "????????????????????????", "oj", "oji", "oji", "oji", ""),
        Language639("Old Church Slavonic", "?????????? ????????????????????", "cu", "chu", "chu", "chu", ""),
        Language639("Oromo", "Afaan Oromoo", "om", "orm", "orm", "orm", ""),
        Language639("Oriya", "???????????????", "or", "ori", "ori", "ori", ""),
        Language639("Ossetian", "???????? ??????????", "os", "oss", "oss", "oss", ""),
        Language639("Panjabi", "??????????????????, ???????????????", "pa", "pan", "pan", "pan", ""),
        Language639("P??li", "????????????", "pi", "pli", "pli", "pli", ""),
        Language639("Persian", "??????????", "fa", "fas", "", "fas", ""),
        Language639("Polish", "j??zyk polski, polszczyzna", "pl", "pol", "pol", "pol", "pols"),
        Language639("Pashto", "????????", "ps", "pus", "pus", "pus", ""),
        Language639("Portuguese", "portugu??s", "pt", "por", "por", "por", ""),
        Language639("Quechua", "Runa Simi, Kichwa", "qu", "que", "que", "que", ""),
        Language639("Romansh", "rumantsch grischun", "rm", "roh", "roh", "roh", ""),
        Language639("Kirundi", "Ikirundi", "rn", "run", "run", "run", ""),
        Language639("Reunion Creole", "Kr??ol R??nion??", "rc", "rcf", "rcf", "rcf", ""),
        Language639("Romanian", "limba rom??n??", "ro", "ron", "", "ron", ""),
        Language639("Russian", "??????????????", "ru", "rus", "rus", "rus", ""),
        Language639("Sanskrit", "???????????????????????????", "sa", "san", "san", "san", ""),
        Language639("Sardinian", "sardu", "sc", "srd", "srd", "srd", ""),
        Language639("Sindhi", "??????????????????, ?????????? ?????????????", "sd", "snd", "snd", "snd", ""),
        Language639("Northern Sami", "Davvis??megiella", "se", "sme", "sme", "sme", ""),
        Language639("Samoan", "gagana fa'a Samoa", "sm", "smo", "smo", "smo", ""),
        Language639("Sango", "y??ng?? t?? s??ng??", "sg", "sag", "sag", "sag", ""),
        Language639("Serbian", "???????????? ??????????", "sr", "srp", "srp", "srp", ""),
        Language639("Scottish Gaelic", "G??idhlig", "gd", "gla", "gla", "gla", ""),
        Language639("Shona", "chiShona", "sn", "sna", "sna", "sna", ""),
        Language639("Sinhalese", "???????????????", "si", "sin", "sin", "sin", ""),
        Language639("Slovak", "sloven??ina, slovensk?? jazyk", "sk", "slk", "", "slk", ""),
        Language639("Slovene", "slovenski jezik, sloven????ina", "sl", "slv", "slv", "slv", ""),
        Language639("Somali", "Soomaaliga, af Soomaali", "so", "som", "som", "som", ""),
        Language639("Southern Sotho", "Sesotho", "st", "sot", "sot", "sot", ""),
        Language639("Spanish", "espa??ol", "es", "spa", "spa", "spa", ""),
        Language639("Sundanese", "Basa Sunda", "su", "sun", "sun", "sun", ""),
        Language639("Swahili", "Kiswahili", "sw", "swa", "swa", "swa", ""),
        Language639("Swati", "SiSwati", "ss", "ssw", "ssw", "ssw", ""),
        Language639("Swedish", "svenska", "sv", "swe", "swe", "swe", ""),
        Language639("Tamil", "???????????????", "ta", "tam", "tam", "tam", ""),
        Language639("Telugu", "??????????????????", "te", "tel", "tel", "tel", ""),
        Language639("Tajik", "????????????, to??ik??, ???????????????", "tg", "tgk", "tgk", "tgk", ""),
        Language639("Thai", "?????????", "th", "tha", "tha", "tha", ""),
        Language639("Tigrinya", "????????????", "ti", "tir", "tir", "tir", ""),
        Language639("Tibetan Standard", "?????????????????????", "bo", "bod", "", "bod", ""),
        Language639("Turkmen", "T??rkmen, ??????????????", "tk", "tuk", "tuk", "tuk", ""),
        Language639("Tagalog", "Wikang Tagalog", "tl", "tgl", "tgl", "tgl", ""),
        Language639("Tswana", "Setswana", "tn", "tsn", "tsn", "tsn", ""),
        Language639("Tonga", "faka Tonga", "to", "ton", "ton", "ton", ""),
        Language639("Turkish", "T??rk??e", "tr", "tur", "tur", "tur", ""),
        Language639("Tsonga", "Xitsonga", "ts", "tso", "tso", "tso", ""),
        Language639("Tatar", "?????????? ????????, tatar tele", "tt", "tat", "tat", "tat", ""),
        Language639("Twi", "Twi", "tw", "twi", "twi", "twi", ""),
        Language639("Tahitian", "Reo Tahiti", "ty", "tah", "tah", "tah", ""),
        Language639("Uyghur", "???????????????????, Uyghurche", "ug", "uig", "uig", "uig", ""),
        Language639("Ukrainian", "????????????????????", "uk", "ukr", "ukr", "ukr", ""),
        Language639("Urdu", "????????", "ur", "urd", "urd", "urd", ""),
        Language639("Uzbek", "O??zbek, ??????????, ???????????????", "uz", "uzb", "uzb", "uzb", ""),
        Language639("Venda", "Tshiven???a", "ve", "ven", "ven", "ven", ""),
        Language639("Vietnamese", "Ti???ng Vi???t", "vi", "vie", "vie", "vie", ""),
        Language639("Volap??k", "Volap??k", "vo", "vol", "vol", "vol", ""),
        Language639("Walloon", "walon", "wa", "wln", "wln", "wln", ""),
        Language639("Welsh", "Cymraeg", "cy", "cym", "", "cym", ""),
        Language639("Wolof", "Wollof", "wo", "wol", "wol", "wol", ""),
        Language639("Western Frisian", "Frysk", "fy", "fry", "fry", "fry", ""),
        Language639("Xhosa", "isiXhosa", "xh", "xho", "xho", "xho", ""),
        Language639("Yiddish", "????????????", "yi", "yid", "yid", "yid", ""),
        Language639("Yoruba", "Yor??b??", "yo", "yor", "yor", "yor", ""),
        Language639("Zhuang", "Sa?? cue????, Saw cuengh", "za", "zha", "zha", "zha", ""),
        Language639("Zulu", "isiZulu", "zu", "zul", "zul", "zul", ""),
    )
}