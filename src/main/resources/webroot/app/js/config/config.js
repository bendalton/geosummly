app.Config = function() {

	function Location(params) {
		this.bounds = params.bounds;
		this.jsonUrl = params.jsonUrl;
		this.zoom = params.zoom;
		this.getCenter();
	}
	Location.prototype.getCenter = function() {
		if (!this.center) {
			this.center = {
				lat: this.bounds.sud + ((this.bounds.north - this.bounds.sud) / 2),
				lng: this.bounds.west + ((this.bounds.est - this.bounds.west) / 2 )
			};
		}
		return this.center;
	};

	return {
		defaultLoc: 'trentino',
		key: {
			leaflet: '5f9ebf625acb4df3a40163ddca8c064b'
		},
		locations: {
			torino: new Location({
				jsonUrl: 'data/torino/clustering-output-eps0.07071067811865477.geojson',
				bounds: {
					north: 45.10975600522702,
					sud: 45.04393354716772,
					west: 7.630176544189453,
					est: 7.734889984130859
				},
				zoom: 11
			}),
			trentino: new Location({
				jsonUrl: 'data/trentino/clustering-output-eps0.09.geojson',
				bounds: {
					north: 46.53633684901995,
					sud: 45.672795442796335,
					west: 10.914315493899755,
					est: 11.831262564937385
				},
				zoom: 11
			})
		}
	};
};