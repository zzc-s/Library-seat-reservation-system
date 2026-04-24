const fs = require('fs');
function replaceStyle(vuePath, link) {
  let s = fs.readFileSync(vuePath, 'utf8');
  const i = s.indexOf('<style scoped>');
  const j = s.indexOf('</style>', i) + 8;
  if (i === -1 || j < 8) { console.error('not found', vuePath); return false; }
  s = s.slice(0, i) + link + s.slice(j);
  fs.writeFileSync(vuePath, s);
  return true;
}
const base = 'f:/基于SpringBoot+Vue的图书馆座位预约系统的设计与实现/frontend/src/views';
replaceStyle(base + '/Home.vue', '<style scoped src="../styles/views/Home.css"></style>');
replaceStyle(base + '/MyReservations.vue', '<style scoped src="../styles/views/MyReservations.css"></style>');
console.log('Home and MyReservations done');
