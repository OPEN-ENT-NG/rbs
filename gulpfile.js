const gulp = require("gulp");
const webpack = require("webpack-stream");
const merge = require("merge2");
const replace = require("gulp-replace");
const clean = require("gulp-clean");
const argv = require("yargs").argv;
const fs = require("fs");

function dropCache() {
  return gulp
    .src(["./src/main/resources/public/dist"], {
      read: false,
      allowEmpty: true,
    })
    .pipe(clean());
}

function buildDev() {
  return gulp
    .src("./src/main/resources/public")
    .pipe(webpack(require("./webpack.config.js")))
    .on("error", function handleError() {
      this.emit("end"); // Recover from errors
    })
    .pipe(gulp.dest("./src/main/resources/public/dist"));
}

function build(done) {
  const refs = gulp
    .src("./src/main/resources/view-src/**/*.+(html|json)")
    .pipe(replace("@@VERSION", Date.now()))
    .pipe(gulp.dest("./src/main/resources/view"));

  const copyBehaviours = gulp
    .src("./src/main/resources/public/dist/behaviours.js")
    .pipe(gulp.dest("./src/main/resources/public/js"));

  merge[(refs, copyBehaviours)];
  done();
}

gulp.task("drop-cache", dropCache);
gulp.task("build-dev", buildDev);
gulp.task("build", build);

function getModName(fileContent) {
  const getProp = function (prop) {
    return fileContent.split(prop + "=")[1].split(/\r?\n/)[0];
  };
  return (
    getProp("modowner") + "~" + getProp("modname") + "~" + getProp("version")
  );
}

function watchFiles() {
  let springboard = argv.springboard;
  if (!springboard) {
    springboard = "../springboard-open-ent/";
  }
  if (springboard[springboard.length - 1] !== "/") {
    springboard += "/";
  }

  gulp.watch(
    "./src/main/resources/public/ts/**/*.ts",
    gulp.series("drop-cache", "build-dev", "build")
  );

  fs.readFile("./gradle.properties", "utf8", function (err, content) {
    const modName = getModName(content);
    gulp.watch(["./src/main/resources/public/js"], () => {
      console.log("Copying resources to " + springboard + "mods/" + modName);
      gulp
        .src("./src/main/resources/**/*")
        .pipe(gulp.dest(springboard + "mods/" + modName));
    });
    gulp.watch(
      [
        "./src/main/resources/public/template/**/*.html",
        "!./src/main/resources/public/template/entcore/*.html",
      ],
      () => {
        console.log("Copying resources to " + springboard + "mods/" + modName);
        gulp
          .src("./src/main/resources/**/*")
          .pipe(gulp.dest(springboard + "mods/" + modName));
      }
    );

    gulp.watch("./src/main/resources/view/**/*.html", () => {
      console.log("Copying resources to " + springboard + "mods/" + modName);
      gulp
        .src("./src/main/resources/**/*")
        .pipe(gulp.dest(springboard + "mods/" + modName));
    });
  });
}

gulp.task("watch", watchFiles);

exports.watch = gulp.parallel("watch");
exports.build = gulp.series("drop-cache", "build-dev", "build");
