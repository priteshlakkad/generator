const Palette = (() => {

	const DEFAULT_SIZE = {
		staticText: { width: 120, height: 20 },
		textField: { width: 120, height: 20 },
		image: { width: 60, height: 40 }
	};

	function init(container) {
		container.querySelectorAll('.palette-item').forEach((item) => {
			item.setAttribute('draggable', 'true');
			item.addEventListener('dragstart', (e) => {
				e.dataTransfer.setData('text/plain', item.dataset.elementType);
				e.dataTransfer.effectAllowed = 'copy';
			});
		});
	}

	function handleDrop(event, band, state, onDone) {
		const type = event.dataTransfer.getData('text/plain');
		const size = DEFAULT_SIZE[type];
		if (!size) return;

		const rect = event.currentTarget.getBoundingClientRect();
		const zoom = state.zoom;
		const x = Math.max(0, Math.round((event.clientX - rect.left) / zoom));
		const y = Math.max(0, Math.round((event.clientY - rect.top) / zoom));

		const element = {
			type,
			x, y,
			width: size.width,
			height: size.height,
			expressionClass: 'java.lang.String',
			border: false
		};
		if (type === 'staticText') element.text = 'New text';
		if (type === 'textField') element.expression = 'null';
		if (type === 'image') element.expression = 'null';

		band.elements.push(element);
		state.selected = { bandName: band.name, index: band.elements.length - 1 };
		onDone();
	}

	return { init, handleDrop };
})();
