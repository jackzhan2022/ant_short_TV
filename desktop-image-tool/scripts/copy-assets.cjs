const fs = require('node:fs');
for (const name of ['index.html', 'styles.css']) {
  fs.copyFileSync(`src/${name}`, `dist/${name}`);
}
for (const name of ['icon.png', 'icon.ico']) {
  fs.copyFileSync(`assets/${name}`, `dist/${name}`);
}
