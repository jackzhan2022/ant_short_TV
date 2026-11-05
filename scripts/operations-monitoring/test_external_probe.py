import unittest
from external_probe import observe, deliver, healthy, send
from unittest.mock import patch, MagicMock


class ProbeTest(unittest.TestCase):
    def test_three_failures_two_successes_and_cooldown(self):
        state = {}
        for at in (0, 30):
            observe(state, False, at)
            self.assertEqual(state.get('pending', []), [])
        observe(state, False, 60)
        self.assertTrue(state['active'])
        incident = state['incident']
        self.assertEqual(len(state['pending']), 1)
        observe(state, False, 90)
        self.assertEqual(len(state['pending']), 1)
        observe(state, True, 120)
        self.assertTrue(state['active'])
        observe(state, True, 150)
        self.assertFalse(state['active'])
        self.assertEqual([x['transition'] for x in state['pending']], ['FIRING', 'RESOLVED'])
        self.assertTrue(all(x['incident'] == incident for x in state['pending']))

    def test_delivery_failure_has_bounded_retries_and_preserves_stable_id(self):
        state = {}
        for at in (0, 30, 60):
            observe(state, False, at)
        with patch('external_probe.send', return_value=False):
            for at in (60, 120, 420, 1320, 2220):
                deliver(state, 'https://mock.invalid', '', 'fixture', at)
        self.assertEqual(state['pending'][0]['status'], 'FAILED')
        self.assertEqual(state['pending'][0]['attempts'], 5)

    def test_health_requires_minimal_up_response(self):
        self.assertTrue(healthy(200, {'status': 'UP', 'build': 'v1'}))
        self.assertFalse(healthy(200, {'status': 'DOWN'}))
        self.assertFalse(healthy(503, {'status': 'UP'}))
        self.assertFalse(healthy(200, {'success': True}))

    def test_http_success_requires_explicit_business_zero_and_never_calls_remote(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        with patch('external_probe.urllib.request.urlopen', return_value=response):
            for body, expected in ((b'{"code":0}', True), (b'{"code":1}', False),
                                   (b'{"StatusCode":0}', True), (b'{}', False),
                                   (b'{"code":false}', False), (b'[]', False), (b'null', False)):
                response.read.return_value = body
                self.assertEqual(send('https://mock.invalid', '', 'test'), expected)


if __name__ == '__main__':
    unittest.main()
