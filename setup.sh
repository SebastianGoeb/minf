#!/bin/bash

dir=$(cd "$(dirname "$0")"; pwd)

echo "**** SETTING UP FLOODLIGHT CONTROLLER ****"
cd $dir

# Clone
if [[ ! -d floodlight ]]; then
    rm -rf floodlight
    git clone git@github.com:floodlight/floodlight.git floodlight
else
    cd "$dir/floodlight"
    git reset --hard
    git clean -fd
fi

# Checkout consistent version
# TODO tested to work with this version of Floodlight. Use release version of Floodlight when available
cd "$dir/floodlight"
git checkout bdddcb4810c04a3eef77c54567ee6728d1674162

# Init submodules
git submodule update --init

# Create symlinks to custom controller code
paths=(
	"src/main/resources/floodlightdefault.properties"
	"src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule"
	"src/main/java/net/floodlightcontroller/proactiveloadbalancer"
	"src/main/java/net/floodlightcontroller/hierarchicalheavyhitters"
)
for path in ${paths[@]}; do
	[ -f $path ] && rm $path
	[ -d $path ] && rm -rf $path
	ln -s "$dir/controller/$path" $path
done

# Setup eclipse project
ant eclipse

# Replace .project file (include linked resources)
cat >.project <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>floodlight</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
	</natures>
	<linkedResources>
		<link>
			<name>src/main/resources/floodlightdefault.properties</name>
			<type>1</type>
			<location>$dir/controller/src/main/resources/floodlightdefault.properties</location>
		</link>
		<link>
			<name>src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule</name>
			<type>1</type>
			<location>$dir/controller/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule</location>
		</link>
		<link>
			<name>src/main/java/net/floodlightcontroller/proactiveloadbalancer</name>
			<type>2</type>
			<location>$dir/controller/src/main/java/net/floodlightcontroller/proactiveloadbalancer</location>
		</link>
		<link>
			<name>src/main/java/net/floodlightcontroller/hierarchicalheavyhitters</name>
			<type>2</type>
			<location>$dir/controller/src/main/java/net/floodlightcontroller/hierarchicalheavyhitters</location>
		</link>
	</linkedResources>
</projectDescription>
EOF
