Due to ZIP (epub3) issues with https://github.com/psiegman/epublib/tree/master , https://github.com/documentnode/epub4j/tree/main and https://github.com/positiondev/epublib 
this was taken from https://github.com/gedoor/legado/tree/master/modules/book/src/main/java/me/ag2s on commit 2f59065228c7b4bac9c0156eaeb80b5c83b284e5

No external lib for legado book module was found online, and as such was imported directly like this. The only thing that may be modified is the EPUB_GENERATOR_NAME.

https://github.com/gedoor/legado forked https://github.com/positiondev/epublib that forked https://github.com/psiegman/epublib/tree/master

The original library (psiegman/epublib) licence is GNU Lesser General Public License v3.0:

epublib is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

