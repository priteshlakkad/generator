# Local quotation images

Place verified product images here using their catalogue code, for example:

`ABT-WHT-FSBTCF2011.jpg`

Names must match the uppercase catalogue code. PNG, JPG and JPEG are supported,
checked in that order. Refresh the quotation after adding/replacing an image;
no application restart is needed.

For all Jaquar quotation templates, resolution order is:
1. This directory (configurable with `pdf.render.images-dir`).
2. Bundled `src/main/resources/static/quotation-assets/jaquar/<CODE>.*` images.
3. The item's `imageUrl`, then `contentUrl` (direct image content URLs).
4. The bundled Jaquar logo.

Place `jaquar-logo.png` here to override the header's bundled brand image.
